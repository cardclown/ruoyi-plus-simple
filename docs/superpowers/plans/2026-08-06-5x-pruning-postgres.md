# RuoYi-Vue-Plus 5.X Pruning and PostgreSQL Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Produce a buildable RuoYi-Vue-Plus 5.X backend that retains native multi-tenancy, RBAC, code generation, Redis, OSS/MinIO, and protected Actuator endpoints while using PostgreSQL.

**Architecture:** Keep the 5.X tenant and system modules intact, remove optional feature modules at their Maven boundaries, then remove the small number of cross-module Java references. Use one PostgreSQL core schema as the authoritative local database and keep infrastructure in Docker.

**Tech Stack:** Java 17 bytecode on Microsoft JDK 21.0.12, Maven 3.9.12, Spring Boot 3.5, MyBatis-Plus, Sa-Token, PostgreSQL 17, Redis, MinIO, Docker Desktop.

## Global Constraints

- Preserve `ruoyi-common-tenant`, `SysTenant`, `SysTenantPackage`, tenant login selection, tenant cache isolation, and `tenant_id` columns.
- Preserve users, departments, posts, roles, menus, data permissions, and `ruoyi-generator`.
- Preserve Actuator and its Basic Auth filter, but remove Spring Boot Admin server and client registration.
- Preserve Redis, OSS, MinIO, API documentation, logging, Excel, idempotency, rate limiting, encryption, and masking.
- Do not apply or drop `stash@{0}`.
- Do not import workflow or job SQL.

---

### Task 1: Remove optional Maven modules and dependency management

**Files:**
- Modify: `pom.xml`
- Modify: `ruoyi-admin/pom.xml`
- Modify: `ruoyi-common/pom.xml`
- Modify: `ruoyi-common/ruoyi-common-bom/pom.xml`
- Modify: `ruoyi-modules/pom.xml`
- Modify: `ruoyi-modules/ruoyi-system/pom.xml`
- Delete: `ruoyi-common/ruoyi-common-job/`
- Delete: `ruoyi-common/ruoyi-common-mail/`
- Delete: `ruoyi-common/ruoyi-common-sms/`
- Delete: `ruoyi-common/ruoyi-common-social/`
- Delete: `ruoyi-modules/ruoyi-demo/`
- Delete: `ruoyi-modules/ruoyi-job/`
- Delete: `ruoyi-modules/ruoyi-workflow/`
- Delete: `ruoyi-extend/`

**Interfaces:**
- Consumes: official 5.X Maven reactor.
- Produces: reactor containing admin, retained common libraries, system, generator, and tenant modules only.

- [ ] Remove the listed module directories after validating every resolved path is inside the repository.
- [ ] Remove their `<module>` declarations and internal artifact dependency-management entries.
- [ ] Remove Spring Boot Admin, SnailJob, WarmFlow, JustAuth, SMS4J, mail, social, demo, job, and workflow dependencies.
- [ ] Keep `spring-boot-starter-actuator`, `ruoyi-common-security`, and `ruoyi-common-tenant` references.
- [ ] Run `rg` against all POM files and confirm deleted artifact IDs are absent.

### Task 2: Reduce authentication to password login without breaking tenants

**Files:**
- Modify: `ruoyi-admin/src/main/java/org/dromara/web/controller/AuthController.java`
- Delete: `ruoyi-admin/src/main/java/org/dromara/web/controller/CaptchaController.java`
- Modify: `ruoyi-admin/src/main/java/org/dromara/web/service/SysLoginService.java`
- Modify: `ruoyi-admin/src/main/java/org/dromara/web/service/SysRegisterService.java`
- Modify: `ruoyi-admin/src/main/java/org/dromara/web/service/impl/PasswordAuthStrategy.java`
- Delete: `ruoyi-admin/src/main/java/org/dromara/web/service/impl/EmailAuthStrategy.java`
- Delete: `ruoyi-admin/src/main/java/org/dromara/web/service/impl/SmsAuthStrategy.java`
- Delete: `ruoyi-admin/src/main/java/org/dromara/web/service/impl/SocialAuthStrategy.java`
- Delete: `ruoyi-admin/src/main/java/org/dromara/web/service/impl/XcxAuthStrategy.java`
- Delete: non-password login bodies under `ruoyi-common-core/domain/model/`
- Delete: social CRUD files under `ruoyi-system`

**Interfaces:**
- Consumes: `/auth/login`, `/auth/logout`, `/auth/register`, `/auth/tenant/list`.
- Produces: password-only authentication with mandatory tenant validation and no captcha dependency.

- [ ] Remove social binding/callback/unlock endpoints while retaining tenant list resolution.
- [ ] Remove social registration logic and dependencies from `SysLoginService`.
- [ ] Remove captcha validation from password login and registration.
- [ ] Remove unused login bodies and social persistence classes.
- [ ] Ensure tenant ID still flows from login body into `TenantHelper.dynamic` and `checkTenant`.

### Task 3: Remove workflow integration from retained core and tenant code

**Files:**
- Delete: workflow DTOs, events, `WorkflowService`, and task-assignee types in `ruoyi-common-core`
- Delete: `ruoyi-modules/ruoyi-system/.../SysTaskAssigneeServiceImpl.java`
- Modify: `ruoyi-modules/ruoyi-system/.../SysTenantServiceImpl.java`

**Interfaces:**
- Consumes: tenant creation transaction.
- Produces: tenant creation that initializes role, department, user, dictionary, and configuration without attempting workflow synchronization.

- [ ] Remove the conditional WarmFlow synchronization block and imports from tenant creation.
- [ ] Delete workflow-only shared contracts and the system task-assignee adapter.
- [ ] Search retained Java sources for workflow and task-assignee types and require zero results.

### Task 4: Clean runtime configuration and configure PostgreSQL

**Files:**
- Modify: `ruoyi-admin/src/main/resources/application.yml`
- Modify: `ruoyi-admin/src/main/resources/application-dev.yml`
- Modify: `ruoyi-admin/src/main/resources/application-prod.yml`
- Modify: `script/docker/docker-compose.yml`
- Modify: `script/docker/nginx/conf/nginx.conf`
- Delete: `.run/ruoyi-monitor-admin.run.xml`
- Delete: `.run/ruoyi-snailjob-server.run.xml`

**Interfaces:**
- Consumes: local Docker services on ports 5432, 6379, 9000, and 9001.
- Produces: application profiles using the PostgreSQL driver and a three-service local compose stack.

- [ ] Remove Spring Boot Admin client registration, SnailJob, mail, SMS, social, captcha, and WarmFlow configuration.
- [ ] Retain Actuator exposure and management username/password properties used by the Basic Auth filter.
- [ ] Replace the MySQL JDBC dependency and URLs with PostgreSQL.
- [ ] Set bounded Hikari pool sizes appropriate for the local single service.
- [ ] Make the compose definition match the existing PostgreSQL, Redis, and MinIO containers and remove obsolete Nginx upstreams.

### Task 5: Clean SQL while preserving tenant and RBAC data

**Files:**
- Modify: `script/sql/postgres/postgres_ry_vue_5.X.sql`
- Modify: core MySQL, Oracle, and SQL Server schema files only where removed feature menus/tables are embedded
- Delete: all job and workflow SQL scripts
- Delete: `script/leave/`

**Interfaces:**
- Consumes: PostgreSQL 17 `psql` with `ON_ERROR_STOP=1`.
- Produces: core tenant/RBAC/generator seed database without job, workflow, demo, or social-login data.

- [ ] Remove `sys_social` and removed-feature menu/client seed rows.
- [ ] Preserve `sys_tenant`, `sys_tenant_package`, `sys_user`, `sys_dept`, `sys_post`, `sys_role`, `sys_menu`, association tables, and generator menus.
- [ ] Ensure the default tenant package menu list references only retained menu IDs.
- [ ] Validate PostgreSQL statements in a fresh database before replacing the active local database.

### Task 6: Build and initialize infrastructure

**Files:**
- Verify: complete Maven reactor
- Execute: `script/sql/postgres/postgres_ry_vue_5.X.sql`

**Interfaces:**
- Consumes: local JDK 21, Maven 3.9.12, and Docker containers.
- Produces: build artifact plus initialized local PostgreSQL database.

- [ ] Run the full Maven package with tests compiled.
- [ ] Capture current database/table counts, terminate target-database sessions, and recreate only the exact application database.
- [ ] Import the PostgreSQL script with `ON_ERROR_STOP=1`.
- [ ] Query tenant, tenant-package, RBAC, and generator records and confirm removed tables are absent.
- [ ] Verify PostgreSQL readiness, authenticated Redis `PING`, MinIO health endpoint, and a clean Git diff check.
