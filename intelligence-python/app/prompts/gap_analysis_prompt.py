"""Skill gap analysis prompt templates (match-tied and standalone).

Both templates carry ``{{SENTINEL}}`` placeholders substituted with
``str.replace`` rather than f-strings or ``str.format``: the prompt bodies are
JSON schemas full of literal braces that either would try to interpret. Same
reasoning as the Phase 3 extraction prompt.

Note the difference in task from Phase 3. Extraction forbade inference — it read
facts off the page. Gap analysis is the opposite: the gaps are given, and the
model's job is to reason about *why they matter for this role* and *how to close
them*. The prompts therefore push hard for role-specific wording, because the
failure mode here is not hallucination but blandness ("learn Kubernetes").
"""

JOB_TITLE_SENTINEL = "{{JOB_TITLE}}"
COMPANY_SENTINEL = "{{COMPANY_NAME}}"
MISSING_SKILLS_SENTINEL = "{{MISSING_SKILLS}}"
MISSING_MUST_HAVES_SENTINEL = "{{MISSING_MUST_HAVES}}"
PREFERRED_MISSING_SENTINEL = "{{PREFERRED_MISSING}}"
RESUME_SUMMARY_SENTINEL = "{{RESUME_SUMMARY}}"
OVERALL_SCORE_SENTINEL = "{{OVERALL_SCORE}}"
REQUIRED_GAP_LINES_SENTINEL = "{{REQUIRED_GAP_LINES}}"
RESUME_TEXT_SENTINEL = "{{RESUME_TEXT}}"

GAP_ANALYSIS_PROMPT_TEMPLATE = """You are a career coach analysing why a candidate does not yet fit a specific role.

The gap detection has already been done. Your task is judgement, not detection:
decide how much each missing item matters FOR THIS ROLE, and say concretely how to close it.

ROLE
Job title: {{JOB_TITLE}}
Company: {{COMPANY_NAME}}
Current match score: {{OVERALL_SCORE}} out of 100

GAPS ALREADY IDENTIFIED
Missing must-have requirements (hard gating criteria): {{MISSING_MUST_HAVES}}
Missing required skills: {{MISSING_SKILLS}}
Missing preferred skills (nice to have): {{PREFERRED_MISSING}}

CANDIDATE RESUME (extract)
\"\"\"
{{RESUME_SUMMARY}}
\"\"\"

PRIORITY RULES
Assign every gap exactly one priority, decided by which list it came from:
- CRITICAL: the item appears in the missing must-have list. Its absence is likely
  disqualifying on its own.
- HIGH: the item appears in the missing required skills list. Expected for the role,
  but not an automatic rejection.
- MEDIUM: the item appears in the missing preferred skills list. Strengthens the
  application; never blocks it.
- LOW: a gap you inferred from the resume and role rather than one listed above.
  Add at most two of these, and only when the resume clearly implies them.

WRITING THE RECOMMENDATION
This is the part that must not be generic. A recommendation that would read the same
for any job is a failed recommendation.
- Name the specific sub-topics to learn, not just the technology.
- Tie it to how this role uses it, quoting the role or company where it helps.
- Build on what the resume already shows: name the candidate's existing adjacent
  experience and route the learning through it.
The example below is about a DIFFERENT skill and a DIFFERENT candidate than the ones
you are given. Copy its shape - specific sub-topics, tied to the role, routed through
existing experience - never its words.
- BAD: "Learn Elasticsearch."
- GOOD: "Learn Elasticsearch index mapping, analyzers and aggregation queries. This
  role's search-relevance work depends on it, and your existing PostgreSQL full-text
  search experience means query semantics are already familiar - the new ground is
  cluster sharding and relevance tuning."
Write about the actual skills listed above, using this candidate's actual background.

ESTIMATING TIME
estimated_weeks is an integer: realistic full-time-study weeks to reach working
competence, not mastery.
- A CLI tool or a library on a language you know: 1-2
- A substantial framework or platform: 4-8
- A new programming language or a deep domain: 8-12
Use null only when you genuinely cannot estimate.

QUICK WINS AND DEAL BREAKERS
- quick_wins: the skill names you estimated at 1 week or less. These are the items the
  candidate could plausibly close before applying. Use [] if none qualify.
- deal_breakers: only CRITICAL gaps where the resume shows NO adjacent or transferable
  experience whatsoever. If the resume shows related work, the gap is closeable and does
  NOT belong here. Use [] if none qualify. Be strict: an over-full deal_breakers list is
  useless to the candidate.

REQUIRED OUTPUT ITEMS
Your "gaps" array MUST contain one object for each line below, in this order, copying
the "skill" value character-for-character and using the priority given:
{{REQUIRED_GAP_LINES}}

OUTPUT RULES
1. Produce exactly one object per required line above - no fewer. Do not merge two
   lines into one, and do not rename, shorten, or paraphrase a skill value.
2. Write a distinct reason and learning_recommendation for every single one. An entry
   with an empty or duplicated recommendation is a failed answer.
3. After the required items you MAY add at most 2 inferred gaps with priority LOW.
4. Never omit a key. Every key in the schema must appear, even when empty.
5. priority must be exactly one of CRITICAL, HIGH, MEDIUM, LOW.

OUTPUT SCHEMA
{
  "gap_summary": string,
  "gaps": [
    {
      "skill": string,
      "priority": "CRITICAL" or "HIGH" or "MEDIUM" or "LOW",
      "reason": string,
      "learning_recommendation": string,
      "estimated_weeks": integer or null
    }
  ],
  "quick_wins": [string],
  "deal_breakers": [string]
}

gap_summary is 2-3 sentences: where the candidate stands for this specific role and what
would move the needle most.

Return only the JSON object. No explanation. No markdown."""


STANDALONE_GAP_PROMPT_TEMPLATE = """You are a career coach reviewing a resume on its own, with no specific job in mind.

Your task: judge which technical domains this candidate is strong in, which are thin or
absent, and what they should add next to broaden their options.

RULES
1. Base every judgement on what the resume actually shows. Do not invent experience.
2. strong_domains: 3 to 5 broad areas the resume evidences well (for example "backend
   API development", "cloud infrastructure", "data engineering"). Name domains, not
   individual tools.
3. weak_domains: 3 to 5 domains a candidate with this profile would normally be expected
   to cover but this resume barely touches. Judge relative to the seniority and direction
   the resume itself suggests - a backend engineer is not weak for lacking mobile skills.
4. recommended_additions: exactly 5 specific, named skills worth learning next. Choose
   things adjacent to the existing strengths, so each one is a realistic next step rather
   than a career change.
5. general_assessment: 2-3 sentences on the candidate's overall profile and direction.
6. Never omit a key. Every key in the schema must appear, even when empty.

RESUME
\"\"\"
{{RESUME_TEXT}}
\"\"\"

OUTPUT SCHEMA
{
  "strong_domains": [string],
  "weak_domains": [string],
  "recommended_additions": [string],
  "general_assessment": string
}

Return only the JSON object. No explanation. No markdown."""


def _format_list(values) -> str:
    """Render a gap list for the prompt.

    A JSON-ish bracketed list keeps the model anchored to the exact strings it
    must echo back in "skill". The explicit "(none)" matters: an empty ``[]``
    reads as an oversight to a small model, which then invents entries to fill
    it.
    """
    if not values:
        return "(none)"
    return "[" + ", ".join(f'"{value}"' for value in values) + "]"


def _build_required_gap_lines(
    missing_must_haves, missing_skills, preferred_missing
) -> str:
    """Enumerate every gap as an explicit, numbered output instruction.

    Restating the gaps as a checklist — rather than relying on the model to walk
    the three lists above — is what stops a small model from answering about
    three of five items. Measured: llama3.2:3b dropped two of five gaps and
    paraphrased a fourth until the required skill strings were spelled out here.
    """
    lines = []
    for items, priority in (
        (missing_must_haves, "CRITICAL"),
        (missing_skills, "HIGH"),
        (preferred_missing, "MEDIUM"),
    ):
        for item in items:
            lines.append(f'{len(lines) + 1}. skill="{item}" priority={priority}')
    return "\n".join(lines) if lines else "(no gaps supplied)"


def build_gap_analysis_prompt(
    job_title: str,
    company_name: str | None,
    missing_skills,
    missing_must_haves,
    preferred_missing,
    resume_text_summary: str,
    overall_score: float,
) -> str:
    """Inject one match's gap context into the match-tied template."""
    return (
        GAP_ANALYSIS_PROMPT_TEMPLATE
        .replace(JOB_TITLE_SENTINEL, job_title or "(not stated)")
        .replace(COMPANY_SENTINEL, company_name or "(not stated)")
        .replace(MISSING_MUST_HAVES_SENTINEL, _format_list(missing_must_haves))
        .replace(MISSING_SKILLS_SENTINEL, _format_list(missing_skills))
        .replace(PREFERRED_MISSING_SENTINEL, _format_list(preferred_missing))
        .replace(RESUME_SUMMARY_SENTINEL, resume_text_summary or "(no resume text available)")
        .replace(OVERALL_SCORE_SENTINEL, f"{overall_score:.2f}")
        .replace(
            REQUIRED_GAP_LINES_SENTINEL,
            _build_required_gap_lines(
                missing_must_haves, missing_skills, preferred_missing
            ),
        )
    )


def build_standalone_gap_prompt(resume_text: str) -> str:
    """Inject ``resume_text`` into the standalone template."""
    return STANDALONE_GAP_PROMPT_TEMPLATE.replace(
        RESUME_TEXT_SENTINEL, resume_text or "(no resume text available)"
    )
