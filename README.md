# FileHarbor ☁️

**A Cloud-Based File Management Application Powered by Microsoft Azure**

FileHarbor is a cloud-based file management application inspired by services like Google Drive. It provides a simple interface for uploading, browsing, downloading, and deleting files, with file storage handled by **Azure Blob Storage**.

The application is built with Angular and Spring Boot and deployed using Microsoft Azure App Services, demonstrating the integration of cloud storage, backend services, and web application hosting.

## 🚀 Live Demo

| Component | Link |
|---|---|
| **Frontend** | [FileHarbor Web Application](https://fileharborclient-hubgaagvfgcxgweq.belgiumcentral-01.azurewebsites.net) |
| **Backend API** | [FileHarbor REST API](https://fileharborapi-ahbhe6f4bqefbjhx.belgiumcentral-01.azurewebsites.net) |

## ✨ Features

- **File Upload:** Upload files directly through the web interface.
- **File Management:** Browse and view files stored in the cloud.
- **File Download:** Retrieve stored files whenever needed.
- **File Deletion:** Remove files from cloud storage.
- **Search & Filtering:** Easily find files.
- **Sorting:** Organize files according to available sorting options.
- **Light & Dark Mode:** Switch between dashboard themes.
- **Responsive Dashboard:** Modern interface built with Angular and Tailwind CSS.
- **Cloud Storage:** Secure file storage using a private Azure Blob Storage container.

## 🛠️ Technology Stack

### Frontend
- Angular 21
- TypeScript
- Tailwind CSS
- Express.js 5 (production server)

### Backend
- Java 21
- Spring Boot 4.1.2
- Maven
- REST API
- Azure Blob Storage SDK

### Cloud Infrastructure
- Microsoft Azure
- Azure App Service (Frontend & Backend Hosting)
- Azure Blob Storage (File Storage)

### Development Tools
- IntelliJ IDEA
- Visual Studio Code
- Git & GitHub
- Postman

## 🏗️ Architecture

FileHarbor follows a simple three-component architecture:

1. **Frontend:** Angular provides the user interface and communicates with the backend through HTTP requests.
2. **Backend:** Spring Boot exposes REST endpoints and handles file management operations.
3. **Cloud Storage:** Azure Blob Storage stores uploaded files in a private container.

### Architecture Flow

```text
        USER
         |
         v
   Angular Frontend
         |
         | HTTP Requests
         v
   Spring Boot REST API
         |
         | Azure Storage SDK
         v
   Azure Blob Storage
```

The frontend and backend are independently deployed using Azure App Service, while Azure Blob Storage handles persistent file storage.

## 📡 REST API

The backend exposes the following endpoints:

| Method | Endpoint | Description |
|---|---|---|
| POST | `/api/files` | Upload a file |
| GET | `/api/files` | Retrieve the list of files |
| GET | `/api/files/{filename}` | Download a file |
| DELETE | `/api/files/{filename}` | Delete a file |

## 📂 Project Structure

```text
FileHarbor/
│
├── backend/
│   └── Spring Boot application
│
├── frontend/
│   └── Angular application
│
├── docs/
│   └── Project documentation
│
└── README.md
```

## ⚙️ Local Development Setup

### Prerequisites

Make sure you have the following installed:

- Java 21
- Maven
- Node.js and npm
- Angular CLI
- An Azure Storage Account with a Blob container

### 1. Clone the Repository

```bash
git clone https://github.com/YOUR_USERNAME/FileHarbor.git
cd FileHarbor
```

### 2. Configure the Backend

Navigate to the backend directory:

```bash
cd backend
```

Configure the following environment variables:

| Variable | Description |
|---|---|
| `AZURE_STORAGE_CONNECTION_STRING` | Azure Storage Account connection string |
| `AZURE_STORAGE_CONTAINER` | Azure Blob container name |
| `CORS_ALLOWED_ORIGINS` | Allowed frontend origins |

Example:

```text
AZURE_STORAGE_CONNECTION_STRING=your_connection_string
AZURE_STORAGE_CONTAINER=your_container_name
CORS_ALLOWED_ORIGINS=http://localhost:4200
```

Run the Spring Boot application:

```bash
mvn spring-boot:run
```

The backend will be available at:

`http://localhost:8080`

### 3. Configure the Frontend

Open another terminal and navigate to the frontend directory:

```bash
cd frontend
```

Install dependencies:

```bash
npm install
```

Start the Angular development server:

```bash
npm run dev
```

The frontend will be available at:

`http://localhost:4200`

**Note:** Make sure the frontend development environment points to the local backend API.

## ☁️ Azure Deployment

FileHarbor is deployed on Microsoft Azure using the following services:

| Azure Service | Purpose |
|---|---|
| Azure App Service | Hosts the Angular frontend |
| Azure App Service | Hosts the Spring Boot backend |
| Azure Blob Storage | Stores uploaded files |

### Deployment Architecture

```text
         Internet
             |
             v
    Azure App Service
      (Frontend)
             |
             v
    Azure App Service
       (Backend)
             |
             v
     Azure Blob Storage
       (Private Container)
```

The frontend and backend are deployed independently, allowing each application component to be updated without redeploying the other.

## 🔒 Security & Configuration

- Azure Blob Storage uses a private container.
- Storage credentials are configured through environment variables.
- Sensitive configuration values are not included in the source code.
- Backend CORS configuration controls which frontend origins can access the API.

**Important:** Never commit Azure Storage connection strings, credentials, or other sensitive environment variables to the repository.

## 📌 Project Status

**Core application successfully implemented and deployed.**

- [x] Angular frontend development
- [x] Spring Boot REST API
- [x] Azure Blob Storage integration
- [x] File upload, listing, download, and deletion
- [x] Search, filtering, and sorting
- [x] Light and dark mode
- [x] Azure App Service deployment
- [x] Production environment configuration
- [x] Production CRUD workflow testing

## 🎯 Project Objectives

FileHarbor was developed as part of a university's project **Cloud Foundations** course.

Its main objectives are to demonstrate:

- Cloud application development.
- REST API integration.
- Cloud-based file storage.
- Application hosting using Azure App Service.
- Integration between backend services and Azure infrastructure.
- Deployment and configuration of cloud applications.

## 👨‍💻 Author

**Safwene Nachab**

Software engineer

GitHub: [@Safwene17](https://github.com/Safwene17)

---

*Built with Angular, Spring Boot, and Microsoft Azure.*