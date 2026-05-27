# Phase 2 Technical Implementation Plan

## 1. Microservices Architecture - Diogo Sousa
The core architecture focuses on high availability and loose coupling through message-driven patterns.

### Data & Communication
* **Database Replication:** Implementation of replication strategies to ensure data persistence and availability across microservices. 
* **Message-Driven** Utilization of **RabbitMQ** for asynchronous, decoupled communication. 
    * **Orchestration:** Deployed within **Kubernetes** for scalable message processing. 
    * **Integration:** Support for **Quarkus** (Java) and **Python** clients to facilitate real-time data processing. 

---

## 2. Serverless & FaaS Integration - Bruno Faustino
To optimize resource usage, specific background tasks (such as detail filling) will transition to a Serverless model using **Google Cloud Functions**.

* **Implementation:**  **Quarkus Funqy** for a portable, provider-agnostic function-as-a-service (FaaS) experience. 
* **Communication:** Utilizing **gRPC** for high-performance communication between the serverless functions and existing microservices. 

---

## 3. DevOps & Observability
A multi-layered approach to infrastructure management and system monitoring.

### 3.1 Observability Stacks - Bruno Faustino
We are thinking about two primary observability stacks for monitoring metrics, logs, and traces.

#### Option A: The LGTM Stack (Grafana Cloud)
* **Loki**  Log aggregation system using label-based indexing for cost-efficiency. 
* **Grafana** Central visualization hub for dashboards and alerting. 
* **Tempo** High-scale distributed tracing backend.
* **Mimir/Prometheus** Long-term storage and collection of time-series metrics. 
* **Alloy** OpenTelemetry-native collector for forwarding metrics, logs and traces data. 

#### Option B: VictoriaMetrics Stack
* **VictoriaMetrics:** Handles metrics and alerts via MetricsQL. 
* **VictoriaLogs:** Dedicated log database using LogsQL. 
* **VictoriaTraces:** Dedicated tracing backend. 
* **Ingestion:** Native **OpenTelemetry** support for all telemetry types. 

### 3.2 Infrastructure as Code (IaC) & Automation

#### 3.2.1 GKE Automation - Rodrigo Neto
* **GKE Automation:** Provisioning Google Kubernetes Engine (GKE) using **Terraform** or **Pulumi** (Python). 

#### 3.2.2 Configuration Management and Continuous Delivery - Bruno Faustino
* **Configuration Management:** Using **Ansible** to manage Kubernetes ConfigMaps and software state. 
* **Continuous Delivery:** **Argo CD** will be used for GitOps, ensuring the cluster state automatically reflects the Git repository via pull requests.

---

## 4. Security & Identity Management - Rodrigo Neto
Security is enforced at both the application level (JWT) and the network level (Service Mesh). 

### 4.1 Authentication Flow
The Authentication Microservice (AMS) manages the user lifecycle: 
1.  **Account Creation:** AMS validates credentials and stores the user hash in a database. 
2.  **Authentication:** User logs in, AMS validates credentials, generates a **JWT**, and stores it in a **Redis** cache for session management. 
3.  **Request Validation:** The user provides the JWT in request headers.  Microservices validate the token with AMS (or via Service Mesh policy) to grant access.

### 4.2 Network & Bot Security
* **Istio Service Mesh:** Enforces **mTLS** (mutual TLS) for secure service-to-service communication and decentralized authorization. 
* **Discord Bot Validation:** Action validation between the Discord bot and backend services is secured via a shared secret. 
    * Requests are encrypted with the shared secret, including an account ID and timestamp to prevent replay attacks.

---

## 5. Technical Architecture Diagram
![Technical Application Architecture](./app_technical_architecture.png)

---