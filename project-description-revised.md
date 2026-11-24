# Distributed Real-Time Payment and Inventory System — Project Proposal

> Offline-first retail POS and payment reconciliation for the Kenyan market

This proposal outlines the design and implementation of a Distributed Real-Time Payment and Inventory System built specifically to thrive in the Kenyan market environment. The proposed solution bridges the gap between digital payment collection and actual business certainty.

The system introduces a hybrid technology approach designed to keep businesses running regardless of internet stability. The user interface and checkout screens are built using Kotlin Multiplatform, a technology that allows the system to store data locally on the store's device. This ensures that cashiers can continue to ring up sales and manage physical stock even when the internet completely fails.

For the central server, the system uses a dual-language approach. JavaScript is used to handle rapid, incoming messages from the internet, such as instant payment confirmations from mobile money networks. Rust, a language known for its extreme safety and ability to handle heavy computational loads without crashing, is used to synchronize data across multiple store branches and ensure no sales are duplicated or lost when a disconnected store finally comes back online.

This proposal details the background of the problem, evaluates existing market solutions, and presents a comprehensive execution plan covering software design, data processing, and offline-first synchronization techniques. The ultimate goal of this project is to provide Kenyan retailers with a reliable, scalable, and fully compliant business management tool that operates flawlessly regardless of infrastructure challenges.

## Chapter One: Introduction

### 1.1 Background

To fully grasp the necessity of this project, it is vital to examine the massive scale and the current operational state of the Kenyan retail and small business sector. Official data indicates that there are over 7.4 million Micro, Small, and Medium Enterprises operating in Kenya today. These businesses collectively employ approximately 14.9 million people, which represents a massive portion of the national workforce. The sector is so significant that it contributes approximately 40 percent to the national Gross Domestic Product. In recent years, these small businesses accounted for an astonishing 85 percent of all new jobs created in the country.

Despite this massive economic scale, the sector operates in a highly informal manner. Of the 7.4 million businesses, only about 1.6 million are formally licensed and registered, leaving a staggering 5.8 million businesses operating entirely in the informal economy.

In this vast market, mobile money is the undisputed king of payments. Safaricom's M-Pesa network processes hundreds of billions of dollars annually and is used by roughly 96 percent of Kenyan adults. It is the default payment method for everything from large electronics purchases to tiny daily grocery runs.

However, this heavy reliance on mobile money creates a massive administrative burden for business owners. When a customer pays via M-Pesa, the payment confirmation data lives securely within the telecommunications company's network, while the operational data — what specific item was sold, who the cashier was, and how much stock is left — lives in a completely separate cash register, a basic spreadsheet, or a paper notebook.

In addition to these payment tracking challenges, the regulatory environment in Kenya has recently shifted in a way that directly impacts everyday retail.

### 1.2 Problem Statement

The core problem this project addresses is the inability of multi-branch businesses to track all sales from a single, centralized point. Without a unified system, companies struggle with a "reconciliation gap". When a business operates across multiple locations, data fragmentation becomes inevitable, leading to daily mobile money mismatches and lost visibility into real-time inventory levels. Existing cloud-based checkout systems often fail in these environments; they rely on constant internet connectivity, causing operational paralysis when connections drop. The result is a profound lack of a resilient, offline-capable system that can automatically synchronize data across branches, maintain real-time inventory accuracy, and ensure strict compliance with national tax regulations.

### 1.3 Objectives

#### General Objective

The primary objective of this project is to design, develop, and deploy a distributed, offline-first payment and inventory management system that fully automates the matching of mobile money transactions to store sales, synchronizing data flawlessly across multiple retail branches.

#### Specific Objectives

To achieve the general objective, the project will pursue the following specific milestones:

1. To build a highly resilient user interface for store cashiers using Kotlin Multiplatform. This interface must allow retail staff to process sales, scan barcodes, and manage physical inventory directly on the local device, ensuring zero downtime and continuous operation during total internet outages.
2. To seamlessly embed Safaricom's communication bridge (specifically the automated phone prompt feature) directly into the cashier's checkout screen. This will enable instant, automated matching of customer payments to specific store items, completely eliminating the need for cashiers to manually read text messages on a store phone.
3. To design an intelligent background synchronization engine that securely queues pending sales on the store's local computer while offline, and automatically resolves data conflicts (such as two offline cashiers selling the same last item) once network connectivity is restored.

### 1.4 Justification

#### The Economic Cost of Operational Fragmentation

The Kenyan retail sector is overwhelmingly sustained by Micro, Small, and Medium Enterprises (MSMEs), which number over 7.4 million and provide approximately 85 percent of all new employment opportunities nationwide. Despite this massive economic scale, the sector suffers from profound operational fragmentation. The reliance on mobile money, specifically Safaricom's M-Pesa, dictates the financial rhythm of these businesses. However, the manual reconciliation of M-Pesa payment confirmations against physical inventory records creates daily operational bottlenecks.

From a direct economic efficiency standpoint, this manual matching is actively harming business growth. When business owners and finance teams are forced to spend hours every day matching text messages to paper receipts, they waste valuable time that should be redirected toward customer service and business expansion. This inefficiency leads to severe revenue leakage, undetected staff fraud, and inaccurate stock assessments. Fully automating this process is critical to plugging daily financial leaks and ensuring business survival in an environment where most small enterprises fail within their first five years of operation.

The proposed Distributed Real-Time Payment and Inventory System is engineered to resolve these inefficiencies through a highly resilient, mathematically verified technical architecture. The significant time and resources required to develop this system are directly justified by the economic efficiency it introduces—instant, automated matching prevents employee fraud and stops customers from leaving underpaid.

The unique value proposition of this system becomes evident when contrasted with existing retail solutions operating in the Kenyan market, such as Veira, Gigva, and Cute Profit. While these platforms have made significant strides in automating payment reconciliation, they harbor architectural paradigms that limit their resilience in infrastructure-constrained environments:

- **Cute Profit** operates as a fully cloud-based point-of-sale (POS) system connected directly to telecommunications networks and the Kenya Revenue Authority (KRA) electronic Tax Invoice Management System (eTIMS).
- **Gigva** positions itself as a comprehensive Software-as-a-Service (SaaS) platform offering M-Pesa reconciliation and business analytics, but its efficacy remains strictly bound to continuous internet availability.
- **Veira** represents a more advanced market entrant, offering automated M-Pesa integration via the Daraja API alongside an offline-capable cache mechanism. However, Veira's offline capability functions primarily as an asynchronous queuing mechanism. If concurrent updates occur across multiple disconnected branches, these systems typically rely on simplistic "Last-Writer-Wins" (LWW) conflict resolution logic, which can inadvertently overwrite valid offline transactions and cause phantom inventory discrepancies.

To directly address the critical vulnerabilities of current market leaders, the proposed system introduces a hybrid architectural approach that moves beyond mere offline caching. It implements a mathematically rigorous "local-first" resilience model using Kotlin Multiplatform, allowing store operations to continue undisturbed during network outages. By combining this local-first client with a specialized split-backend—utilizing JavaScript for rapid network ingestion and a high-performance Rust processing engine for data synchronization—the system ensures robust performance and data integrity where competitors typically fail. Leveraging Conflict-Free Replicated Data Types (CRDTs) built within the Rust engine, the system guarantees deterministic, mathematical data convergence without manual intervention or data loss. For instance, if cashiers in two disconnected branches simultaneously sell the final units of a shared inventory item, the CRDT infrastructure independently tracks increments and decrements, eventually syncing to a true, mathematically accurate inventory state upon network restoration. This transition from simplistic overwrite mechanisms to autonomous, deterministic synchronization elevates the project from a standard POS application to an enterprise-grade distributed system.

Beyond its technical architecture, the commercialization potential of the proposed solution is substantial when structured as a multi-tenant SaaS platform tailored for MSMEs and multi-branch retailers. The economic viability of retail technology in Kenya is highly sensitive to hardware constraints and upfront capital expenditures. Traditional POS hardware can cost between KES 50,000 and KES 150,000, creating an insurmountable barrier for smaller merchants, while per-transaction percentage fees continuously erode narrow retail profit margins. The proposed system bypasses these barriers by adopting an architecture that runs seamlessly on commodity Android tablets and smartphones, drastically lowering the Total Cost of Ownership (TCO). The SaaS commercialization strategy entails a flat-fee subscription model engineered for sustainability:

- **Baseline Tier:** Designed for single-store operators requiring basic M-Pesa STK push integration and KRA eTIMS compliance. Cross-branch synchronization is out of scope at this tier.
- **Enterprise Tier:** Built for distributed retail chains requiring advanced CRDT inventory synchronization, real-time business intelligence, and multi-branch scalability. The four business intelligence reports detailed in §3.2, and all multi-branch features, are delivered at this tier.

[TODO — author: state the flat monthly subscription fee in KES for each tier. §1.4 promises a "flat-fee subscription model" but no figure is given anywhere in the document.]

Because the system inherently prevents revenue leakage associated with unverified M-Pesa transactions and automates the costly administrative burden of tax compliance, the return on investment for the merchant is realized rapidly. This ensures low platform churn and long-term commercial viability beyond the project's academic inception.

### 1.5 Project Boundaries and Limitations

The boundaries of this project encompass the complete design, software development, and functional testing of the retail software prototype. The user-facing application boundary is explicitly limited to a checkout and inventory management interface built using Kotlin Multiplatform. Designed for deployment primarily on Android-based touchscreen tablets and standard desktop computers within retail environments, this client ensures high reliability. To enable robust offline capability during network outages, local data storage and synchronization states are strictly managed via a localized database tool called SQLDelight.

The central server boundary operates on a decoupled, three-tier architecture engineered for extreme horizontal scalability. This split-backend approach utilizes two distinct environments to ensure that subsequent integrations can be executed without disrupting foundational synchronization logic:

- **The JavaScript Gateway:** A Node.js environment developed exclusively to handle incoming internet traffic, direct routing, and the rapid ingestion of webhooks and payment confirmation messages from telecommunications providers.
- **The Rust Processing Engine:** A secondary, highly secure compiled binary service that manages the complex internal rules of the system. This includes maintaining inventory counts, resolving Conflict-Free Replicated Data Type (CRDT) collisions when offline devices attempt concurrent updates, and ensuring the memory-safe merging of offline data into the primary system.

During its initial phase, the external communication boundary is strictly localized to integrating Safaricom's M-Pesa Daraja API (specifically Paybill, Buy Goods, and STK Push JSON schemas) and ensuring Kenya Revenue Authority (KRA) eTIMS compliance. The project intentionally excludes the integration of traditional physical credit card swipe machines, international payment gateways, or alternative minority mobile money wallets to focus entirely on the dominant payment provider in the Kenyan market.

However, the architectural scalability of the system accommodates seamless future expansion. The JavaScript Gateway layer utilizes a standardized adapter design pattern. By normalizing disparate incoming payment payloads into a standardized internal transaction schema before passing them to the Rust core, this modularity allows for the rapid future integration of alternative regional payment gateways—such as Airtel Money, Equitel, or cross-border facilitators like Pesapal—achieving truly agnostic, multi-provider payment reconciliation.

The system is designed to support scalable deployment topologies as commercial adoption grows. The initial deployment strategy utilizes a centralized, managed server model to simplify demonstration and prototyping. Because the backend relies on stateless JavaScript nodes for network ingestion and compiled Rust binaries for high-performance memory safety, the architecture can easily transition into a fully containerized, hybrid-cloud deployment orchestrated via Kubernetes. Future iterations are engineered to leverage edge computing, strategically placing distributed synchronization nodes closer to rural retail clusters to reduce latency and bandwidth consumption during peak operational hours.

Although currently bounded as a retail point-of-sale and localized inventory manager, the core processing engine is adaptable to diverse business types beyond general retail, including service-based industries, hospitality, and wholesale logistics. The system's output is specifically structured to serve as an ingestible feed for broader enterprise environments. By exposing secure, RESTful endpoints and utilizing webhook event emissions, the platform is positioned to operate as the decentralized physical retail arm of a centralized corporate ecosystem. This extensibility allows it to feed real-time, mathematically consistent sales data into larger Enterprise Resource Planning (ERP) platforms—such as Odoo, QuickBooks, or SAP—facilitating sophisticated supply chain management and automated financial accounting at an institutional scale.

### 1.6 Operational Boundaries

The operational environment for this system assumes that the retail business has access to intermittent, rather than continuous, internet connectivity.

## Chapter Two: Literature Review and Case Studies

### Introduction

To design an effective and truly useful software solution, it is critical to critically evaluate the current state of retail technology, digital payment integration, and software architecture currently deployed within the Kenyan context. This chapter reviews existing software systems that attempt to solve the payment matching problem, analyzes their operational strengths and fatal weaknesses, and reviews the deep technical frameworks — such as offline-first synchronization models — that will be utilized to build the proposed solution.

### Case Studies

#### Case Study 1: Veira, Gigva and Cute Profit — Current Retail Payment Systems in Kenya

Several commercial software platforms have recently emerged in the Kenyan market specifically to address the daily nightmare of matching mobile money payments to store sales. Systems like Veira and Gigva focus heavily on the fast-paced retail and supermarket sector. Gigva, for example, offers an automated management screen that matches mobile payments to physical sales within seconds. It is designed to separate mixed payments where a customer pays half in physical cash and half via mobile money and automatically triggers alerts when physical stock levels fall too low. Veira takes a similar approach, focusing on tracking financial mismatches at the individual transaction level rather than leaving them as a massive, confusing lump sum at the end of the month. The Veira system immediately flags any sale entered by a cashier that does not receive a matching mobile payment within a few seconds, preventing the customer from leaving the store with unpaid goods. Another notable market solution is Cute Profit, which positions itself as a fully cloud-based checkout system that connects directly to both the telecommunications networks and the national tax authority systems. While these platforms successfully solve the manual accounting and reconciliation problem, a deep review of their architecture reveals that they heavily rely on continuous, uninterrupted cloud connectivity.

#### Case Study 2: Daraja API Telecommunications Integration Patterns

Safaricom's communication bridge, known as the Daraja system, acts as the vital link connecting third-party software applications to the massive mobile money ecosystem. The most prominent and useful feature for physical retail stores is the automated phone prompt. This feature automatically sends a pop-up message to a customer's phone, asking them to simply enter their secret PIN to authorize a payment for the exact amount of their groceries, rather than forcing them to navigate long menus and manually type in the store's business number and the payment amount.

When a customer enters their PIN, the telecommunications company sends an automated internet message containing the payment details to a specific receiving address on the store's central server. Best engineering practices dictate that the store's server must immediately respond to this message with a success signal before doing any other work. Failure to respond instantly causes the telecommunications company to assume the message was lost, prompting them to send it again, which can cause the store's system to record the payment twice and severely confuse the accounting records. Furthermore, robust systems must implement safe update methods — a design principle ensuring that even if the same payment message arrives five times due to a network glitch, the system recognizes the duplicate identification number and only records the payment once.

#### Case Study 3: Offline-First Application Architectures

To decisively solve the internet connectivity issues that plague purely cloud-based checkout systems, modern software engineering has shifted toward an "offline-first" architecture. In these systems, the local device sitting on the store counter is treated as the primary, most important point of interaction. The central internet server acts only as a secondary backup and synchronization mechanism.

Kotlin Multiplatform has rapidly emerged as a leading framework for building these highly resilient systems. It allows developers to write the core business rules and database instructions once, and then share that exact same code across Android tablets, Apple devices, and Windows desktop computers, ensuring perfect consistency. A critical component of making this offline architecture work is a tool called SQLDelight. This tool manages a highly efficient, lightweight database directly on the store's device, saving all product names, prices, and tax rates locally.

Recent engineering case studies on how to synchronize data in these offline systems highlight a vital design principle known as the "Server Is Always Right" rule. In this model, if a cashier makes a sale while the internet is down, the sale is saved on the local device and marked with a "PENDING" label. A silent background process constantly watches this local database. The moment the internet comes back, the background process quietly pushes the pending sales to the central server. Concurrently, a separate process listens for new information coming down from the server. If a conflict occurs — for instance, if a cashier in Branch A and a cashier in Branch B both lose internet, and both sell the very last bottle of water in the company's shared inventory — the system defers to the central server's timestamp to resolve the conflict gracefully. This prevents the system from crashing and simply alerts the manager to the physical stock discrepancy.

That server-timestamp rule is Last-Writer-Wins (LWW), which is precisely the weakness §1.4 identifies in Veira: a valid offline sale can be silently overwritten and the inventory counts driven out of true. It describes the state of current engineering practice, not the design of this project. The proposed system replaces it, in the Rust core, with the deterministic CRDT merge — PN-Counters and OR-Sets — set out in §1.4. Case Study 4 examines the formal literature this local-first approach is drawn from.

#### Case Study 4: Distributed Retail Systems and Local-First Architectures

The evolution of retail management systems has historically followed the broader trajectory of enterprise computing: transitioning from isolated, on-premise mainframe systems to globally distributed, cloud-based applications. While cloud architectures solved the problem of data centralization and multi-branch visibility, they introduced a severe dependency on persistent network connectivity. In infrastructure-constrained regions, reliance on uninterrupted cloud access routinely leads to operational paralysis.

Recent literature highlights a paradigm shift toward "Local-First Software," a conceptual framework formalized by Kleppmann et al. (2019), which advocates for architectures where the primary copy of the data resides on the local device rather than a distant server. Under this framework, the central cloud server is demoted from its role as the absolute gatekeeper of data to a secondary synchronization node and backup repository. This model prioritizes speed, multi-device support, and offline functionality, ensuring that applications remain highly available regardless of network status.

Real-world applications of this architecture are increasingly documented in edge-computing scenarios. For example, case studies examining offline-first POS systems deployed in geographically isolated environments—such as the operational deployment at Bamboo's Kitchen in remote areas—demonstrate that treating the local device as the primary computational authority radically reduces checkout latency and completely eliminates business disruption during extended telecommunications outages.

#### Case Study 5: Comparative Analysis of Existing Retail Management Systems

To clearly justify the necessity of the proposed technical architecture, a comparative analysis of the leading retail platforms operating within the Kenyan market was conducted. The analysis reveals a distinct gap regarding deterministic offline data synchronization and architectural resilience.

| System Feature / Capability | Veira | Gigva | Cute Profit | Proposed System |
| --- | --- | --- | --- | --- |
| **Primary Architecture** | Cloud-first with local offline caching. | Pure Cloud SaaS platform. | Pure Cloud SaaS platform. | Local-First Distributed Architecture. |
| **M-Pesa Reconciliation** | Automated via Daraja API integration. | Automated via Daraja API integration. | Automated via Daraja API integration. | Automated via Daraja API with idempotency tracking. |
| **Offline Capability** | Yes (Asynchronous caching and queuing). | No (Requires persistent connection). | No (Requires persistent connection). | Yes (Full local database read/write via SQLDelight). |
| **Conflict Resolution Strategy** | Server-timestamp overwrites (LWW). | N/A (Cloud consensus). | N/A (Cloud consensus). | Deterministic Merge (PN-Counters and OR-Sets). |
| **Target Hardware** | Android devices / Dedicated Terminals. | Web Browser / Cloud environments. | Web Browser / Mobile environments. | Android / Desktop via KMP. |
| **Primary Gap / Limitation** | Risk of silent data loss during concurrent offline branch updates. | Immediate operational paralysis during network outages. | High latency during poor connectivity; operational paralysis during outages. | Requires sophisticated Rust backend deployment; higher initial engineering complexity. |

### Summary

The comprehensive review of existing market systems and technical literature reveals a distinct and highly actionable gap in the market. While modern platforms provide excellent automated matching of payments to sales, their heavy reliance on continuous internet connectivity makes them highly vulnerable to standard infrastructure failures in the region. Conversely, while the national tax authority has provided offline fallback tools like the basic phone shortcode service, these are entirely manual, tedious, and drastically slow down the checkout process for fast-moving retail goods. The literature on advanced software architecture clearly demonstrates that true operational resilience requires an offline-first approach using local databases on the device and highly intelligent background synchronization engines. However, maintaining high-speed connections to the telecommunications company and the tax authority simultaneously requires a central server capable of handling massive amounts of incoming traffic without buckling.

## Chapter Three: System Implementation and Technical Design

### Introduction

This chapter details the specific, step-by-step procedures that will be followed to execute the development of the project.

### 3.1 Software Design

The software architecture is designed around a distributed, offline-first philosophy, broken into three highly specialized, distinct layers: the Client Layer, the Gateway Layer, and the Core Processing Layer.

| Architectural Layer | Technologies Used | Primary Responsibility |
| --- | --- | --- |
| **Client Layer (Store Front)** | Kotlin Multiplatform, Compose Multiplatform, SQLDelight | Displaying the checkout screen, storing local product data, queuing sales when the internet is dead. |
| **Gateway Layer (Internet Doors)** | JavaScript (Node.js) | Handling incoming messages from mobile networks, routing traffic, talking to the national tax systems. |
| **Core Layer (The Engine)** | Rust, PostgreSQL Database | Merging offline data, resolving conflicts, locking inventory counts, running complex financial reports. |

#### The Client Layer (Store Front)

The visual interface will be developed using modern design tools within a Kotlin Multiplatform project. The most critical design feature here is local data storage. Using the SQLDelight tool, the application will maintain a highly compressed, local database containing the store's entire product catalog, barcodes, prices, and specific tax rates right on the device.

#### Notification and Exception Module

Operational efficiency within a distributed retail network depends heavily on system responsiveness and situational awareness. To support this, the architecture includes a dedicated, WebSocket-driven notification module that streams critical alerts directly to administrative dashboards or store tablets, ensuring that any anomalies are addressed the moment they occur.

The notification module performs several vital roles. It sends Low Stock Alerts to the manager's interface when physical counts drop, ensuring shelves remain stocked. Additionally, it monitors network status, generating Synchronization Failure alerts if a specific store terminal has not established a heartbeat connection with the Rust backend for a set period, such as 24 hours. This proactive monitoring prevents local databases from becoming dangerously out of sync. Most importantly, the module provides transparency for automated actions, such as generating Duplicate Transaction alerts. These messages highlight instances where the Daraja API sent duplicate payment payloads, often caused by network lag on the telecommunications side, notifying the manager that the Rust synchronization engine successfully recognized the duplicate receipt strings and deduplicated the entry to maintain accounting integrity. Finally, the system immediately surfaces Payment Exceptions to the cashier. If a customer cancels a mobile prompt or enters an incorrect PIN, the Daraja API returns specific error codes. The JavaScript gateway catches these responses and pushes an instant alert to the cashier's tablet, allowing them to request a different payment method without slowing down the checkout line.

#### Security Measures and Data Integrity

The reliability of national retail infrastructure, especially when connecting to tax authorities and financial networks, requires uncompromising security protocols at every stage of data processing and transit. To prevent unauthorized data interception, cryptographic communication is strictly enforced. All external interactions — including synchronization between the store client and the gateway, webhook deliveries from Daraja, and data sent to the national tax authority — mandate the use of Transport Layer Security (TLS), protecting against man-in-the-middle attacks. API endpoints receiving sensitive webhooks are further secured through IP whitelisting.

Data storage mechanisms prioritize physical security. The local SQLite databases on store devices are fully encrypted using AES-256-GCM standards. If a tablet is stolen, the inventory data, offline logs, and customer details remain inaccessible without the master decryption key, mitigating breach risks. Furthermore, the system's use of Event Sourcing ensures an immutable audit trail. Every state change, like a sale or a voided transaction, is added as a new cryptographic log entry rather than overwriting existing data. This provides a tamper-proof history essential for tax compliance and fraud auditing. Finally, the central database uses continuous Write-Ahead Log (WAL) archiving and daily automated backups stored in geographically isolated buckets, ensuring recovery in case of infrastructure failure.

#### Implementation and Validation: Testing Strategy

To guarantee operational stability and prevent financial issues before launch, the implementation plan involves a multi-tiered testing strategy to systematically validate each architectural layer. Validation begins with extensive Functional Testing of the multiplatform client. This uses UI automation to verify the accuracy of barcode scanning, local cart math, and tax aggregations under isolated conditions. Following this, Integration Testing is conducted in sandbox environments. This phase simulates real-world payment callbacks and tax invoice requests, ensuring the JavaScript Gateway correctly parses varying data schemas and handles telecommunications errors without system failure. Since offline-first resilience is a primary goal, Synchronization and Chaos Testing is the most critical phase. Using specialized chaos engineering tools, engineers will artificially degrade internet speed and completely sever connections during transactions. The system must transition the UI to offline mode without interrupting the cashier. Subsequently, testers will simulate concurrent offline sales of identical items across different branches. Upon reconnecting, the Rust engine must prove mathematical convergence of the inventory counters, accurately updating stock without losing data or creating database deadlocks. To validate scalability, Performance and Load Testing will subject the core engine to high-concurrency stress tests. This involves simulating thousands of simultaneous payment webhooks and synchronization pushes per second to ensure stable CPU usage and system performance.

#### Use Case Diagram

[TODO — author: insert the use case diagram here. "Figure 1: System Use Case Diagram detailing proposed system functionality."]

### 3.2 Preliminary Data Processing & Analysis

The system's data processing pipeline focuses heavily on transforming messy, unstructured payment data into clean, structured, actionable business intelligence.

#### Mobile Money Data Processing

The most critical data processing occurs the moment the mobile network sends back the payment confirmation. The JavaScript system will read the incoming message (see Appendix for the exact structure). The processing logic will look for the specific result code. If the code is 0 (indicating absolute success), the system will extract the exact amount paid, the unique receipt number, and the customer's phone number.

[TODO — author: the Appendix referenced above ("the exact structure") does not exist in this document.]

#### Business Intelligence Reporting

To support informed decision-making, the system will generate the following business intelligence reports:

- **Sales Trends:** Analyzing daily, weekly, and monthly sales volume to identify peaks and dips in revenue.
- **Inventory Movement:** Tracking stock levels to pinpoint high-turnover products and proactively manage replenishment needs.
- **Payment Reconciliation Summaries:** Providing clear, automated overviews comparing mobile money transactions against recorded sales to highlight discrepancies.
- **Branch Performance Analytics:** Aggregating revenue and operational efficiency metrics to allow for comparative analysis across multiple retail locations.

To guarantee that a payment is never counted twice, the processing engine will use a unique transaction identification string as a primary key in its database. Before applying the payment to the pending sale, the system will check if this exact identification string already exists in its records. If it does, the duplicate message is safely ignored and discarded. If it is new, the system changes the sale status on the cashier's screen from "Awaiting Payment" to "Paid," instantly reflecting this change in less than a second.
