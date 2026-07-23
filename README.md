# job-hunt-ai-techie
A serious, end-to-end platform that helps a user upload a resume, paste a JD, get match analysis, identify skill gaps, track applications, and prepare for interviews.


Endpoints to confirm the phase 0 setup: 
1. Checking application health: curl http://localhost:8080/actuator/health
2. Checking connection with python setup: curl http://localhost:8080/api/v1/ping/python
3. Checking connection with Db: curl http://localhost:8080/api/v1/ping/db
4. Checking for connected service/client: curl http://localhost:8000/health
5. Checking if swagger is up: curl http://localhost:8080/swagger-ui/index.html