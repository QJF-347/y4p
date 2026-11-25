# Distributed Real-Time Payment and Inventory System — Project Proposal

> Offline-first retail POS and payment reconciliation for the Kenyan market

This proposal outlines the design and implementation of a Distributed Real-Time Payment and Inventory System built specifically to thrive in the Kenyan market environment. The proposed solution bridges the gap between digital payment collection and actual business certainty.

The system introduces a hybrid technology approach designed to keep businesses running regardless of internet stability. The user interface and checkout screens are built using Kotlin Multiplatform, a technology that allows the system to store data locally on the store's device. This ensures that cashiers can continue to ring up sales and manage physical stock even when the internet completely fails.

For the central server, the system uses a TypeScript application running on Node.js with the Fastify framework, backed by a PostgreSQL database. TypeScript is used to handle rapid, incoming messages from the internet, such as instant payment confirmations from mobile money networks. The same service synchronizes data across multiple store branches and ensures no sales are duplicated or lost when a disconnected store finally comes back online.

This proposal details the background of the problem, evaluates existing market solutions, and presents a comprehensive execution plan covering software design, data processing, and offline-first synchronization techniques. The ultimate goal of this project is to provide Kenyan retailers with a reliable, scalable business management tool that keeps operating through infrastructure challenges.

## Chapter One: Introduction

### 1.1 Background

To fully grasp the necessity of this project, it is vital to examine the massive scale and the current operational state of the Kenyan retail and small business sector. Official data indicates that there are over 7.4 million Micro, Small, and Medium Enterprises operating in Kenya today. These businesses collectively employ approximately 14.9 million people, which represents a massive portion of the national workforce. The sector is so significant that it contributes approximately 40 percent to the national Gross Domestic Product. In recent years, these small businesses accounted for an astonishing 85 percent of all new jobs created in the country.

Despite this massive economic scale, the sector operates in a highly informal manner. Of the 7.4 million businesses, only about 1.6 million are formally licensed and registered, leaving a staggering 5.8 million businesses operating entirely in the informal economy.

In this vast market, mobile money is the undisputed king of payments. Safaricom's M-Pesa network processes hundreds of billions of dollars annually and is used by roughly 96 percent of Kenyan adults. It is the default payment method for everything from large electronics purchases to tiny daily grocery runs.

However, this heavy reliance on mobile money creates a massive administrative burden for business owners. When a customer pays via M-Pesa, the payment confirmation data lives securely within the telecommunications company's network, while the operational data — what specific item was sold, who the cashier was, and how much stock is left — lives in a completely separate cash register, a basic spreadsheet, or a paper notebook.

In addition to these payment tracking challenges, the regulatory environment in Kenya has recently shifted in a way that directly impacts everyday retail.

### 1.2 Problem Statement

The core problem this project addresses is the inability of multi-branch businesses to track all sales from a single, centralized point. Without a unified system, companies struggle with a "reconciliation gap". When a business operates across multiple locations, data fragmentation becomes inevitable, leading to daily mobile money mismatches and lost visibility into real-time inventory levels. Existing cloud-based checkout systems often fail in these environments; they rely on constant internet connectivity, causing operational paralysis when connections drop. The result is a profound lack of a resilient, offline-capable system that can automatically synchronize data across branches and maintain accurate inventory levels.

### 1.3 Objectives

#### General Objective

The primary objective of this project is to design, develop, and deploy a distributed, offline-first payment and inventory management system that fully automates the matching of mobile money transactions to store sales, synchronizing data flawlessly across multiple retail branches.

#### Specific Objectives

To achieve the general objective, the project will pursue the following specific milestones:

1. To build a highly resilient user interface for store cashiers using Kotlin Multiplatform. This interface must allow retail staff to process sales and manage physical inventory directly on the local device, ensuring zero downtime and continuous operation during total internet outages.
2. To seamlessly embed Safaricom's communication bridge (specifically the automated phone prompt feature) directly into the cashier's checkout screen. This will enable instant, automated matching of customer payments to specific store items, completely eliminating the need for cashiers to manually read text messages on a store phone.
3. To design a background synchronization engine inside the Android application that securely queues pending sales on the store device while offline, and reconciles them deterministically once network connectivity is restored — for example, when two offline devices in the same branch both sell from the same stock.

### 1.4 Justification

#### The Economic Cost of Operational Fragmentation

The Kenyan retail sector is overwhelmingly sustained by Micro, Small, and Medium Enterprises (MSMEs), which number over 7.4 million and provide approximately 85 percent of all new employment opportunities nationwide. Despite this massive economic scale, the sector suffers from profound operational fragmentation. The reliance on mobile money, specifically Safaricom's M-Pesa, dictates the financial rhythm of these businesses. However, the manual reconciliation of M-Pesa payment confirmations against physical inventory records creates daily operational bottlenecks.

From a direct economic efficiency standpoint, this manual matching is actively harming business growth. When business owners and finance teams are forced to spend hours every day matching text messages to paper receipts, they waste valuable time that should be redirected toward customer service and business expansion. This inefficiency leads to severe revenue leakage, undetected staff fraud, and inaccurate stock assessments. Fully automating this process is critical to plugging daily financial leaks and ensuring business survival in an environment where most small enterprises fail within their first five years of operation.

The proposed Distributed Real-Time Payment and Inventory System is engineered to resolve these inefficiencies through a highly resilient, offline-first technical architecture. The significant time and resources required to develop this system are directly justified by the economic efficiency it introduces—instant, automated matching prevents employee fraud and stops customers from leaving underpaid.

The unique value proposition of this system becomes evident when contrasted with existing retail solutions operating in the Kenyan market, such as Veira, Gigva, and Cute Profit. While these platforms have made significant strides in automating payment reconciliation, they harbor architectural paradigms that limit their resilience in infrastructure-constrained environments:

- **Cute Profit** operates as a fully cloud-based point-of-sale (POS) system connected directly to telecommunications networks and the Kenya Revenue Authority (KRA) electronic Tax Invoice Management System (eTIMS).
- **Gigva** positions itself as a comprehensive Software-as-a-Service (SaaS) platform offering M-Pesa reconciliation and business analytics, but its efficacy remains strictly bound to continuous internet availability.
- **Veira** represents a more advanced market entrant, offering automated M-Pesa integration via the Daraja API alongside an offline-capable cache mechanism. However, Veira's offline capability functions primarily as an asynchronous queuing mechanism. If concurrent updates occur across multiple disconnected branches, these systems typically rely on simplistic "Last-Writer-Wins" (LWW) conflict resolution logic, which can inadvertently overwrite valid offline transactions and cause phantom inventory discrepancies.

To directly address the critical vulnerabilities of current market leaders, the proposed system introduces a hybrid architectural approach that moves beyond mere offline caching. It implements a "local-first" resilience model using Kotlin Multiplatform, allowing store operations to continue undisturbed during network outages. By combining this local-first client with a TypeScript backend—using Node.js and Fastify for rapid network ingestion and a PostgreSQL database for durable data synchronization—the system ensures robust performance and data integrity where competitors typically fail.

The synchronization service does not attempt to overwrite records with a single "last writer wins" value. Instead, it merges by record type. Sales are treated as append-only events: each sale carries a device-generated identifier, and the server applies it exactly once, decrementing stock by the quantity sold. Because a sale is a relative movement rather than an absolute stock figure, two offline devices that each sell one unit of the same product both apply correctly, and the resulting count is the sum of both movements. Product metadata — names, prices, categories — is merged with the device-supplied timestamp, and the server applies an update only when it is newer than the stored version; a stale update is rejected and recorded instead of silently overwriting fresher data. For instance, if two devices in the same branch both go offline and each sells the last units of a product, reconnecting applies both movements exactly once and the stock count is accurate. This record-type-aware merge is what lifts the project above the simplistic overwrite mechanisms competitors rely on.

Beyond its technical architecture, the system has clear commercialization potential. The economic viability of retail technology in Kenya is highly sensitive to hardware constraints and upfront capital expenditures. Traditional POS hardware can cost between KES 50,000 and KES 150,000, creating an insurmountable barrier for smaller merchants, while per-transaction percentage fees continuously erode narrow retail profit margins. The proposed system bypasses these barriers by running on commodity Android tablets and smartphones, drastically lowering the Total Cost of Ownership (TCO).

The implementation itself is single-tenant: one installation serves one retail business, with an OWNER account and MANAGER accounts scoped to specific branches. There is no shared multi-customer platform, billing engine, or tenant-isolation layer in the project. A tiered subscription model — for example a single-store tier and a multi-branch enterprise tier — is discussed only as a future commercialization path and is not implemented; every account in a deployment receives the same feature set.

Because the system reduces revenue leakage associated with unverified M-Pesa transactions and automates the administrative burden of payment reconciliation, the return on investment for the merchant is realized quickly, supporting low platform churn and long-term commercial viability beyond the project's academic inception.

### 1.5 Project Boundaries and Limitations

The boundaries of this project encompass the complete design, software development, and functional testing of the retail software prototype. The user-facing application boundary includes two applications built on the same backend API: a checkout and inventory interface built with Kotlin Multiplatform, and an owner-facing admin application for Android. Designed for deployment primarily on Android-based touchscreen tablets and standard desktop computers within retail environments, the store client ensures high reliability. To enable robust offline capability during network outages, local data storage and synchronization states are strictly managed via a localized database tool called SQLDelight.

The central server boundary operates on a single TypeScript service backed by a PostgreSQL database, structured in internal layers so that subsequent integrations can be executed without disrupting foundational synchronization logic:

- **The HTTP Gateway:** A TypeScript service built with the Fastify framework (running on Node.js) that handles incoming internet traffic, routing, and the rapid ingestion of payment confirmation messages from the mobile network.
- **The Synchronization Service:** The inventory and sync modules within the same TypeScript service, backed by PostgreSQL. They maintain inventory counts, merge updates from offline devices, and apply every pushed sale exactly once.

During its initial phase, the external communication boundary is localized to Safaricom's M-Pesa Daraja API — specifically the STK Push request and its callback. The admin application provides a settings screen where the operator can enter a Paybill number, a Buy Goods till number, and Daraja credentials (consumer key, consumer secret, passkey, and shortcode). These values are stored locally on the admin device and masked on screen, but they are not yet used to initiate or receive payments; they reserve the configuration for those channels. The project intentionally excludes the integration of traditional physical credit card swipe machines, international payment gateways, or alternative minority mobile money wallets to focus entirely on the dominant payment provider in the Kenyan market.

Although the initial scope is limited to one provider, the gateway is structured so that an additional provider could be added later. Incoming payloads are validated into a single internal transaction schema before the synchronization service records them, so an alternative adapter would not require changes to the core. Airtel Money, Equitel, or cross-border facilitators such as Pesapal are mentioned only as possible future integrations and are not implemented.

The system is designed for a centralized, managed server model in its initial deployment, which simplifies demonstration and prototyping. Because the backend relies on stateless Node.js processes for network ingestion and a PostgreSQL database for durable storage, the architecture could later be deployed as containerized services. Edge computing — placing synchronization nodes closer to rural retail clusters — is a possible future direction rather than part of this implementation.

Although currently bounded as a retail point-of-sale and localized inventory manager, the data model is adaptable to other business types, including service-based industries, hospitality, and wholesale logistics. The system exposes secure RESTful endpoints, so an external system can read its data through the API. Outbound webhook emission and pre-built connectors to ERP platforms such as Odoo, QuickBooks, or SAP are not implemented.

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

Recent engineering case studies on how to synchronize data in these offline systems highlight a vital design principle known as the "Server Is Always Right" rule. In this model, if a cashier makes a sale while the internet is down, the sale is saved on the local device and marked with a "PENDING" label. A background process in the application watches the local database. The moment the internet comes back, it pushes the pending sales to the central server. Concurrently, a separate process pulls new information coming down from the server. If a conflict occurs — for instance, if two terminals in the same store both lose internet and both sell the last bottle of water — the system defers to the central server's record to resolve the discrepancy and surfaces the difference to the manager rather than crashing.

That server-side rule is Last-Writer-Wins (LWW) when it is applied to whole records, and applying it to sales is precisely the weakness §1.4 identifies in Veira: a valid offline sale can be silently overwritten and the inventory counts driven out of true. It describes the state of current engineering practice, not the design of this project. The proposed system avoids it by treating sales as append-only, relative movements applied exactly once, and by using timestamp comparison only for descriptive product metadata — where a stale write is rejected and surfaced instead of silently accepted. §1.4 sets out that split. Case Study 4 examines the formal literature this local-first approach is drawn from.

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
| **Conflict Resolution Strategy** | Server-timestamp overwrites (LWW). | N/A (Cloud consensus). | N/A (Cloud consensus). | Idempotent sale deltas; version-guarded metadata merge. |
| **Target Hardware** | Android devices / Dedicated Terminals. | Web Browser / Cloud environments. | Web Browser / Mobile environments. | Android / Desktop via KMP. |
| **Primary Gap / Limitation** | Risk of silent data loss during concurrent offline branch updates. | Immediate operational paralysis during network outages. | High latency during poor connectivity; operational paralysis during outages. | Requires a stateful synchronization backend and PostgreSQL deployment; higher initial engineering complexity. |

### Summary

The comprehensive review of existing market systems and technical literature reveals a distinct and highly actionable gap in the market. While modern platforms provide excellent automated matching of payments to sales, their heavy reliance on continuous internet connectivity makes them highly vulnerable to standard infrastructure failures in the region. Conversely, the manual fallbacks available to retailers — such as noting payments in a book and reconciling them later — are tedious and drastically slow down the checkout process for fast-moving retail goods. The literature on advanced software architecture clearly demonstrates that true operational resilience requires an offline-first approach that keeps a local database on the device and synchronizes it through a background process. However, accepting rapid payment confirmations from the telecommunications network while also centralizing sales from many offline devices still requires a central server capable of handling concurrent incoming traffic without buckling.

## Chapter Three: System Implementation and Technical Design

### Introduction

This chapter details the specific, step-by-step procedures that will be followed to execute the development of the project.

### 3.1 Software Design

The software architecture is designed around a distributed, offline-first philosophy. It has a Client Layer that contains two applications (a store till and an admin console), a Gateway Layer, and a Core Processing Layer.

| Architectural Layer | Technologies Used | Primary Responsibility |
| --- | --- | --- |
| **Client Layer (Store Front)** | Kotlin Multiplatform, Compose Multiplatform, SQLDelight | Displaying the checkout screen, storing local product data, queuing sales when the internet is unavailable. |
| **Admin Client (Operations)** | Kotlin, Jetpack Compose (Android), Retrofit | Dashboard, branch and inventory management, device register, sales reports and payment reconciliation. |
| **Gateway Layer (Internet Doors)** | TypeScript (Node.js/Fastify) | Validating and routing incoming API requests and M-Pesa callback messages. |
| **Core Layer (The Engine)** | TypeScript (Node.js/Fastify), PostgreSQL Database | Merging offline data, applying sale movements exactly once, guarding inventory updates, and producing financial reports. |

#### The Client Layer (Store Front)

The visual interface will be developed using modern design tools within a Kotlin Multiplatform project. The most critical design feature here is local data storage. Using the SQLDelight tool, the application maintains a local database containing the store's entire product catalog, an optional barcode or SKU field for each product, prices, and per-product tax rates on the device.

#### The Admin Application and Access Control

The project ships two client applications. The store application (Kotlin Multiplatform) is the checkout and inventory surface used by cashiers. The admin application, `orangeadmin`, is a native Android application written with Jetpack Compose and is the owner's operations surface. It shows a dashboard summary; a branch list with today's sales, device counts and low-stock counts; per-branch inventory with restock, create and delete actions; a device register with provisioning and revocation; the payment reconciliation queue; and the sales reports described in §3.2. Its screens load from the same REST API, refresh when opened, and support pull-to-refresh on the dashboard. The admin application keeps last-known-good snapshots for display while offline; it is a read cache, not an offline write client.

Access control is central to the server. Accounts have one of two roles, OWNER or MANAGER. Managers are scoped to a list of branches, while the owner sees the whole business. Users are not self-service: an OWNER creates accounts through an invite-only endpoint that sets the initial password and role. Authentication uses short-lived access tokens plus refresh tokens, and the API enforces branch scope on every branch-bound route. There is no tenant model — one deployment serves one business.

A settings screen in the admin application holds the merchant's M-Pesa configuration: Paybill number, Buy Goods till number, Daraja consumer key, consumer secret, passkey, and shortcode. The values are stored locally on the admin device, masked in the interface, and are not yet sent to any payment endpoint; they reserve the configuration slots for the channels the payment phase will add.

#### Device Provisioning and Lifecycle

A point-of-sale device does not self-register. An OWNER registers a device against a branch in the admin application, and the server issues a device id, a device code, and a device token. The cashier enters those values once on the store device's setup screen, either by scanning the QR code the admin application displays or by pasting the text. The device token authenticates sync and heartbeat calls without a user password. Devices can be revoked, which immediately stops synchronization; a revoked device record can then be permanently deleted, while an active one cannot. The device register records when each device was last seen, so the owner can spot a terminal that has stopped syncing.

#### Synchronization Protocol and Data Integrity

Each store device keeps a local outbox of unsynchronized changes. A synchronization cycle pushes a batch of up to 200 product records and up to 25 sales, and pulls server changes in pages of up to 1,000 records per entity, continuing for up to 20 pages while more remain. The cycle runs at application launch, every 60 seconds while the application is open, and when the cashier taps Sync. Pushed sales are applied exactly once by their client-generated ids and decrement stock as relative movements; product and category metadata is upserted using the device timestamp and rejected when older than the stored row, with a rejected write recorded rather than silently dropped.

The server adds its own integrity layers. Write endpoints accept an `Idempotency-Key` header: a repeated key returns the stored response instead of re-running the write, and a reused key with a different body is rejected. Administrative actions are appended to an audit log with actor, action, target, and timestamp. M-Pesa callbacks are authenticated with a shared secret, deduplicated on the M-Pesa receipt number, and any payment that cannot be matched to a sale is placed in a reconciliation queue for the owner to resolve manually.

#### Notification and Exception Module

Operational efficiency within a distributed retail network depends on system responsiveness and situational awareness. Administrative screens stay current without a persistent push channel: the dashboard and branch views reload when they are opened, and the dashboard also supports pull-to-refresh. This keeps the client simple and works across the range of connections the product targets.

The notification behavior has several parts. Low stock counts are shown directly on the dashboard and branch screens whenever a product falls below the configured threshold. A device that has not synchronized for a set period — such as 24 hours — is flagged in the device register from its last-seen timestamp, which the heartbeat sent with each sync cycle updates, so a terminal that has gone quiet is visible before its local data drifts far out of date.

The module also provides transparency for automated actions. When the Daraja API delivers the same payment message more than once — usually because the network did not receive the first acknowledgement quickly enough — the server recognizes the duplicate M-Pesa receipt number and records the payment only once. Transactions the system cannot match automatically are listed in the reconciliation queue rather than being guessed at, so a manager resolves each exception explicitly.

Finally, payment exceptions are surfaced during the sale. If a customer cancels the mobile prompt or enters an incorrect PIN, the Daraja API returns a specific error code; the gateway passes it to the requesting client so the cashier can choose another payment method without losing the sale.

#### Security Measures and Data Integrity

The reliability of retail infrastructure, where sales, stock and payment records move across many devices, requires deliberate security at each stage. Cryptographic communication is enforced: all client-server traffic — synchronization between the store client and the gateway, admin API calls, and callback deliveries from Daraja — runs over Transport Layer Security (TLS), protecting against man-in-the-middle attacks. M-Pesa callback endpoints are authenticated with a shared callback secret supplied in a request header (or in the query string where the provider requires it), so a caller without the secret is rejected.

Data at rest is protected by the operating system's application sandbox: each application's local database is private to that application and inaccessible to other apps on the device. The project does not implement full-database encryption (for example SQLCipher); encrypting the local SQLite database is noted as a production-hardening step for deployments where devices are shared or more likely to be lost. On the server, integrity relies on an append-only audit log: administrative actions such as device registration, revocation, product changes and user creation are recorded with actor, action, target and timestamp, giving an accountable history. Database backup and archiving are operational deployment concerns and are not part of the application code.

#### Implementation and Validation: Testing Strategy

To guard against financial and data-integrity regressions, the implementation is validated at two levels. The backend has an automated integration test suite that runs against a real PostgreSQL database; it covers authentication and role scoping, the catalog lifecycle (create, immutable-field rejection, restock, soft delete, duplicate names and SKUs), device revocation and permanent deletion, branch deletion rules, idempotency-key replay and reuse rejection, keyset-paginated sales listing, the sync paths, and the payment callback and reconciliation flows. The client's shared Kotlin module contains unit tests for its synchronization and totalling logic. These automated tests are complemented by end-to-end manual testing on real Android devices: a sale is rung up online and offline, the outbox is observed draining after reconnection, and the admin application is checked against the same data. The project does not include UI automation, chaos engineering, or load-testing infrastructure; those were considered and are deliberately out of scope.

#### Use Case Diagram

[TODO — author: insert the use case diagram here. "Figure 1: System Use Case Diagram detailing proposed system functionality."]

### 3.2 Preliminary Data Processing & Analysis

The system's data processing pipeline focuses heavily on transforming messy, unstructured payment data into clean, structured, actionable business intelligence.

#### Mobile Money Data Processing

The most critical data processing occurs the moment the mobile network sends back the payment confirmation. The backend service reads the incoming callback (see Appendix for the exact structure). The processing logic will look for the specific result code. If the code is 0 (indicating absolute success), the system will extract the exact amount paid, the unique receipt number, and the customer's phone number.

[TODO — author: the Appendix referenced above ("the exact structure") does not exist in this document.]

#### Business Intelligence Reporting

To support informed decision-making, the admin application generates the following reports:

- **Sales Trends:** Daily sales totals and sale counts for a branch over a selectable window, drawn as a bar chart so peaks and quiet days are visible at a glance.
- **Branch Performance Comparison:** Per-branch revenue, sale count, low-stock items and registered devices for the same window, shown side by side so branches can be compared directly.
- **Payment Reconciliation Summary:** The working queue of payments that could not be matched to a sale, with amounts and status, for manual resolution.

An Inventory Movement report — tracking stock changes from restocks and sales to highlight high-turnover products — is a natural extension of the stored restock and sale records, but is not built as a dedicated screen in this phase.

To guarantee that a payment is never counted twice, the processing engine will use a unique transaction identification string as a primary key in its database. Before applying the payment to the pending sale, the system will check if this exact identification string already exists in its records. If it does, the duplicate message is safely ignored and discarded. If it is new, the system changes the sale status on the cashier's screen from "Awaiting Payment" to "Paid," reflecting the change as soon as the callback is processed, typically within seconds.
