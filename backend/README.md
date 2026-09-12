# AutoCare — Backend

API REST em **Java 21 + Spring Boot 3.3** para o CMMS de gestão de manutenção.

- **Fase 1** — fundação: autenticação, utilizadores, configuração, catálogo, auditoria.
- **Fatia 1** — núcleo CMMS: organizações multi-tenant, locais, tipos de ativo (+
  sistemas), ativos (viaturas / máquinas / geradores), medidores (horímetro /
  hodómetro) com deteção de inconsistências, matriz de criticidade, **fotografias**.
- **Fatia 2** — plano de manutenção do documento de referência: **checklists** de
  inspeção, **planos de manutenção** (tarefas por sistema, gatilhos por
  horímetro/calendário), atribuição a ativos + cálculo de "próxima manutenção",
  execução de tarefas, e **geração de PDF** do plano.

## Requisitos

- JDK 21
- Maven 3.9+ (ou o `mvn` do sistema)
- Sem base de dados externa em desenvolvimento (H2 em ficheiro, modo PostgreSQL)

## Arranque

```bash
mvn spring-boot:run
```

- API: `http://localhost:8080/api/v1`
- Documentação Swagger: `http://localhost:8080/docs`
- Base de dados de desenvolvimento: `backend/data/autocare.mv.db` (H2)

Com dados de demonstração (conta `demo@autocare.ao` / `demo1234`, admin):

```bash
SEED_DEMO=true mvn spring-boot:run
```

## Testes

```bash
mvn test
```

60 testes: unitários (`JwtServiceTest`, `ExpiryCalculatorTest`) e de integração
(`@SpringBootTest` + `MockMvc`): autenticação, endpoints públicos, persistência,
coerência do esquema Flyway com as entidades JPA, e os módulos CMMS (empresa,
locais, tipos de ativo, ativos, criticidade, medidores).
Os testes usam H2 em memória e limpam os dados entre casos.

> Máquina com pouca RAM: o `pom.xml` limita o surefire a 640 MB, um único fork
> reutilizado. Correr com `MAVEN_OPTS=-Xmx256m` se necessário.

## Estrutura

```
src/main/java/ao/autocare/
  config/            AutoCareProperties, OpenApiConfig, DemoDataSeeder, CmmsDemoSeeder
  common/            ApiException, GlobalExceptionHandler, ErrorResponse, PagedResponse
  domain/            entidades JPA + enums + conversores JSON
  repo/              repositórios Spring Data JPA
  security/          JwtService, JwtAuthenticationFilter, SecurityConfig, AuthPrincipal
  modules/
    auth/            registo (cria empresa), login, refresh (rotação), logout, reposição
    user/            perfil, alterar palavra-passe, exportar/eliminar conta
    org/             OrgContext (multi-tenant) + OrganizationController
    location/        locais — hierarquia empresa → obra → parque → ...
    assettype/       tipos de ativo + 8 sistemas padrão do documento de referência
    asset/           ativos (viatura/máquina/gerador) + matriz de criticidade
    meter/           medidores (horímetro/hodómetro), leituras, inconsistências
    appconfig/       configuração pública (nome do produto alterável) + admin
    catalog/         planos de assinatura, marcas/modelos de referência
    audit/           registo de ações importantes
    health/          estado do serviço
src/main/resources/
  application.yml               configuração (env override)
  db/migration/V1__init.sql             esquema Fase 1
  db/migration/V2__reference_data.sql   planos, marcas/modelos, configuração base
  db/migration/V3__cmms_core.sql        locais, tipos, ativos, medidores, criticidade
```

## Base de dados

O esquema é propriedade das **migrações Flyway** (`ddl-auto=none`). SQL escrito de
forma portável entre H2 (modo PostgreSQL) e PostgreSQL:

- chaves primárias `VARCHAR(36)` (UUID gerado pela aplicação)
- sem enums nativos (VARCHAR + enums Java) nem arrays (JSON em TEXT)

Para produção com PostgreSQL, definir apenas:

```
DATABASE_URL=jdbc:postgresql://host:5432/autocare
DATABASE_USER=...
DATABASE_PASSWORD=...
```

## Variáveis de ambiente principais

| Variável | Omissão |
|---|---|
| `PORT` | 8080 |
| `APP_NAME` / `APP_TAGLINE` | AutoCare / A inteligência da sua viatura |
| `DATABASE_URL` | H2 em ficheiro (`./data/autocare`) |
| `JWT_ACCESS_SECRET` / `JWT_REFRESH_SECRET` | segredos de desenvolvimento (**mudar em produção**) |
| `JWT_ACCESS_TTL` / `JWT_REFRESH_TTL` | PT15M / P30D |
| `CORS_ORIGINS` | `*` |
| `SEED_DEMO` | false |

## Convenções

- **Autenticação exigida por omissão.** Rotas abertas listadas em `SecurityConfig`.
- **Erros nunca técnicos.** `GlobalExceptionHandler` devolve mensagens em português;
  o detalhe fica nos logs.
- **Integrações não configuradas** (email, SMS, GPS, pagamentos) devolvem
  `demoMode: true` e nunca simulam ligação real.
