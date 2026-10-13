# 0. Challenge brief

> **This page is the original challenge brief, kept verbatim** as the source of
> every requirement. The only change is the message template in FR.1.2, whose
> placeholders were lost when the brief was copied; it is restored from the
> sample message. For how each requirement is met, see
> [Requirements & acceptance](02-requirements-and-acceptance.md).

Notify Different Customers through dedicated eMail Distribution lists
=====================================================================

Requirements
============

FR.1. Description
-----------------

#### FR.1.1. New warning letter, OAI or non-compliance report matched to a site

Process already in place for public sources to detect those events, the missing piece is the notification system that have to be used to inform the customers. The current process will call this new notification system through a REST API.

#### FR.1.2. Communication Channel

Send a message to a customer dedicated distribution list by event.

Message content will use the following template:

`Site {Site Name}, located at {Site Address} received a {Authority Name} {Document Type} on {Date}. [{Confidence Level}%]`

*   **Site Name**: Full manufacturing site name.

*   **Site Address**: Main site address.

*   **Authority Name**: Authority that trigger the event (e.g. FDA, EUDRA, MHRA)

*   **Document Type:**

    *   Warning Letter

    *   OAI

    *   Non Compliance Report

*   **Confident Level**: Percentage from 0 to 100, representing how trustable is the internal AI matching process that link the document with the site.


Sample final message would be:

Site Qualifyze Spain, located at Passeig de Gracia 19 Barcelona received a FDA warning letter on 12 June 2026. \[80%\]

#### FR.1.3. Different confidence matches

The message will include the match confidence result. The site and public source will be curated as part of the standard process.

#### FR.1.4. Historical Data

The system shall only notify events detected within the current processing cycle (e.g., since the last scheduled run)

#### FR.1.5. Volume

Weekly could happen between 1 to 5 events affecting between 10 and 25 customers.

#### FR.1.6. Historicization and Tracking

Every change in the entities has to be stored for compliance (ISO 27001/ SOC 2 Type II).

Every successful communication has to be stored for tracking.

Every error has to be stored for tracking.

Track at minimum: notification dispatch events (recipient, timestamp, event reference, delivery status) and any configuration changes to distribution lists.

#### FR.1.7. Reusability

We want to extend the same notification system to support other channels (e.g. Slack or WhatsApp) and different events or transactional notifications.

Challenge
=========

The challenge consist in:

*   Create basic technical documentation

    *   Security Threat Assessment

    *   Metrics

        *   Adoption Metrics

        *   Performance Metrics

*   Create a working project using the following stack

    *   Java 25

    *   Spring Boot 4

    *   Liquibase 5

    *   PostgreSQL 18

*   Test

    *   Integration Test Data: Provide data fixtures or SQL scripts sufficient to run your integration tests in a clean environment.

    *   Coverage >90%: Achieve >90% line coverage from unit and integration tests combined, measured by JaCoCo.

*   Acceptance Criteria

    *   Happy Paths: For the core use case of this new functionality, what criteria have to be fulfilled for the functionality to be considered working successfully.

    *   Other Cases: For known edge cases of this new functionality, what criteria have to be fulfilled for the functionality to be considered working successfully.


You can add any other artifact that you consider necessary.

Evaluation Criteria
===================

*   Clean, production-grade code structure

*   Thoughtful API/component design with justification

*   Appropriate use of the required stack

*   Clarity of documentation and design decisions

*   How you handle uncertainty (explicit assumptions are valued)


Submission Instructions
=======================

GitHub access or Zip file.

---

[Docs index](README.md) · Next: [1. Overview →](01-overview.md)
