# Eduhub Validator Service

A web service for validating Eduhub OOAPI endpoints

## API

`GET /endpoints/{endpointId}/config`

Calls the endpoint with `endpointId` through the Eduhub gateway and
reports if a successful response is received.

On success, responds with a `200 OK` status

On error, responds with a `502 Bad Gateway` status.

## Configuring

The service is configured using environment variables:

```
GATEWAY_URL=https://gateway.test.surfeduhub.nl/
GATEWAY_BASIC_AUTH_USER=USER
GATEWAY_BASIC_AUTH_PASS=PASS
```

## Build

```
make
java -jar target/eduhub-validator-service.jar
# To test:
curl -v 'http://localhost:3002/endpoints/demo04.test.surfeduhub.nl/config'
```


## Run in Docker

```
make docker-build
docker compose up
# To test:
curl -v 'http://localhost:3002/endpoints/demo04.test.surfeduhub.nl/config'
```

## Notes

Relevant repos:

https://github.com/SURFnet/eduhub-validator

https://github.com/SURFnet/apie
