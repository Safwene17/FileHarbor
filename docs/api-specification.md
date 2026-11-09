# FileHarbor — API Specification

Base URL: /api

## Upload File

POST /files

Content-Type: multipart/form-data

## List Files

GET /files

## Download File

GET /files/{id}/download

## Delete File

DELETE /files/{id}

## Health Check

GET /health