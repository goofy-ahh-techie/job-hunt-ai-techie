"""Single-shot JD extraction prompt template.

The template is a plain string carrying a ``{{JD_TEXT}}`` sentinel rather than an
f-string or ``str.format`` placeholder: the prompt body *is* a JSON schema, so it
is full of literal braces that either of those would try to interpret. A sentinel
plus ``str.replace`` needs no escaping and keeps the schema readable exactly as
the model will see it.
"""

JD_TEXT_SENTINEL = "{{JD_TEXT}}"

JD_EXTRACTION_PROMPT_TEMPLATE = """You are a precise information extraction system for job descriptions.

Your task: read the JOB DESCRIPTION below and return a single JSON object describing it.

EXTRACTION RULES
1. Extract ONLY what the job description explicitly states. Never infer, guess, or invent.
2. If the text does not state something, use null (for single values) or [] (for lists).
3. Never omit a key. Every key in the schema must appear in your output, even when empty.
4. Copy skill and qualification wording close to the source. Do not normalise, expand
   acronyms, or substitute synonyms.
5. Do not repeat the same item in more than one list unless the job description itself
   states it twice in different roles.

FIELD DEFINITIONS
- job_title: the role title as advertised.
- company_name: the hiring company. null if not named.
- company_description: text describing the company itself, not the role. null if absent.
- location: work location as stated, including remote/hybrid wording if given.
- employment_type: exactly one of FULL_TIME, PART_TIME, CONTRACT, INTERNSHIP, or null
  if the job description does not say.
- experience_years_min / experience_years_max: integers, years of experience only.
  Read these off any phrasing that states a number of years, whether written with
  a hyphen, the word "to", or a plus sign:
    "5+ years"                      -> min 5,  max null
    "3-6 years"                     -> min 3,  max 6
    "6 to 9 years"                  -> min 6,  max 9
    "at least 4 years"              -> min 4,  max null
    "minimum 2 years, up to 5"      -> min 2,  max 5
  Only use null for both when the job description states no number of years at all.
  Never convert a degree requirement into years.
- responsibilities: what the person will actually do day to day.
- required_skills: skills the job description marks as required, essential, or expected.
- preferred_skills: skills marked preferred, desirable, a plus, a bonus, or nice to have.
  A skill belongs in exactly one of required_skills or preferred_skills, never both.
- qualifications: formal credentials — degrees, certifications, licences, clearances.
- must_have: hard gating criteria a candidate is rejected for missing. These are
  deal-breakers, stated with language like "must", "required", "mandatory", "only
  candidates who". This overlaps required_skills by design: required_skills lists
  technologies, must_have lists the conditions for being considered at all.
- nice_to_have: bonus qualifications that strengthen an application but never block it.
- benefits: compensation, perks, insurance, leave, equity, and similar offerings.
- raw_summary: a 2-3 sentence plain summary of the role. This is the only field where
  you write prose rather than extract.

OUTPUT SCHEMA
{
  "job_title": string,
  "company_name": string or null,
  "company_description": string or null,
  "location": string or null,
  "employment_type": "FULL_TIME" or "PART_TIME" or "CONTRACT" or "INTERNSHIP" or null,
  "experience_years_min": integer or null,
  "experience_years_max": integer or null,
  "responsibilities": [string],
  "required_skills": [string],
  "preferred_skills": [string],
  "qualifications": [string],
  "must_have": [string],
  "nice_to_have": [string],
  "benefits": [string],
  "raw_summary": string
}

JOB DESCRIPTION
\"\"\"
{{JD_TEXT}}
\"\"\"

Return only the JSON object. No explanation. No markdown."""


def build_jd_extraction_prompt(raw_text: str) -> str:
    """Inject ``raw_text`` into the extraction template."""
    return JD_EXTRACTION_PROMPT_TEMPLATE.replace(JD_TEXT_SENTINEL, raw_text)
