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
GATEWAY_BASIC_AUTH=USERNAME:PASS
```

## Notes

example eduhub-validor call

```sh
eduhub-validator \
  --profile rio
  --base-url https://gateway.test.surfeduhub.nl/ \
  --basic-auth USERNAME:PASS \
  --add-header 'x-route: endpoint=demo04.test.surfeduhub.nl' \
  --add-header 'accept: application/json; version=5' \
  --add-header 'x-envelope-response: false'
```

Relevant repos:

https://github.com/SURFnet/eduhub-validator

https://github.com/SURFnet/apie

