# FileHarbor — Architecture

## Technology Stack

### Frontend
- Angular 21+
- TypeScript

### Backend
- Java 21
- Spring Boot
- Maven

### Cloud
- Azure App Service
- Azure Blob Storage
- Azure Managed Identity

## Architecture Overview

The Angular frontend communicates with the Spring Boot
backend through REST APIs.

The Spring Boot backend handles:
- Business logic
- File validation
- REST API requests
- Azure Blob Storage integration

Azure Blob Storage stores the actual documents.

The storage container remains private.

## Authentication

Local development:
- Developer credentials through Azure Identity.

Production:
- Azure Managed Identity.
- Azure RBAC for Blob Storage access.

Application-user authentication must be configured
before exposing file operations publicly.

## Deployment

Azure App Service hosts the application.

The production packaging strategy will be finalized
during the deployment phase.