# 🎵 SonicLab - Back-end API

![Java 21](https://img.shields.io/badge/Java-21-orange.svg)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.1-brightgreen.svg)
![MongoDB](https://img.shields.io/badge/MongoDB-Latest-green.svg)
![Docker](https://img.shields.io/badge/Docker-Enabled-blue.svg)
![License](https://img.shields.io/badge/License-GPLv3-blue.svg)

O **SonicLab** é uma plataforma e marketplace digital desenvolvida especialmente para o ecossistema da produção musical contemporânea. O sistema foi concebido para aproximar criadores de áudio — como sound designers, beatmakers e engenheiros de som — de artistas, produtores e músicos em busca de elementos sonoros de alta qualidade para elevar o nível de suas produções.

A plataforma atua como um ecossistema centralizado para descoberta, audição prévia, distribuição e comercialização de soundkits digitais nos mais diversos formatos essenciais para a indústria da música, incluindo pacotes de bateria e percussão (*Drum Kits*), coleções de loops e timbres melódicos (*Sample Packs*), bancos de timbres para sintetizadores virtuais (*Preset Banks*) e arquivos melódicos/harmônicos (*MIDI Kits*).

Em essência, o SonicLab soluciona o problema da dispersão de arquivos e da falta de padronização na venda de amostras sonoras na internet, reunindo em um só lugar uma comunidade ativa em torno da arte do sound design e da produção musical.

---

## 📑 Sumário

- [O Que o Sistema Faz](#-o-que-o-sistema-faz)
- [Funcionalidades](#-funcionalidades)
- [Tecnologias Utilizadas](#-tecnologias-utilizadas)
- [Pré-requisitos e Instalação](#-pré-requisitos-e-instalação)
- [Variáveis de Ambiente e Configuração](#-variáveis-de-ambiente-e-configuração)
- [Documentação da API com Swagger](#-documentação-da-api-com-swagger)
- [Autenticação e Autorização](#-autenticação-e-autorização)
- [Exemplos de Uso](#-exemplos-de-uso)
- [Tratamento de Erros](#-tratamento-de-erros)
- [Licença e Contribuição](#-licença-e-contribuição)

---

## 🎧 O Que o Sistema Faz

O sistema foi arquitetado para atender de ponta a ponta as demandas do fluxo de trabalho musical, entregando valor direto tanto para quem consome quanto para quem cria:

- **Exploração e Catálogo Inteligente:** Uma vitrine interativa onde músicos e produtores podem pesquisar conteúdos por palavras-chave, explorar categorias específicas, filtrar por faixas de preço e navegar através de tags temáticas (como gêneros musicais, instrumentos ou ambiências), encontrando com agilidade o som exato que precisam para um arranjo.
- **Audição e Avaliação em Tempo Real:** Permite que os visitantes escutem faixas de demonstração e acompanhem o desenho sonoro de cada kit antes de efetuar qualquer download ou compra, garantindo transparência, fidelidade auditiva e confiança na escolha dos materiais sonoros.
- **Espaço para Criadores e Produtores:** Dá aos sound designers autonomia total para publicar, editar, precificar e gerenciar seu próprio catálogo de produtos. Cada criador pode disponibilizar suas criações completas com artes visuais de capa, descrições detalhadas, especificações técnicas e áudios de demonstração.
- **Gestão de Identidade e Acesso:** Fornece um ambiente protegido para gerenciamento de contas, permitindo que cada usuário tenha acesso personalizado aos seus kits cadastrados, processos de recuperação de acesso à conta e controle transparente de sua presença na plataforma.

---

## ✨ Funcionalidades

- **Autenticação & Usuários**:
  - Cadastro de usuários com validação forte de credenciais (regras de complexidade e unicidade de e-mail).
  - Login com emissão de token JWT (`access_token`).
  - Fluxo seguro de recuperação de senha com tokens de uso único e envio de e-mail assíncrono.
  - Controle de perfis e permissões (`USER` e `ADMIN`).
- **Segurança & Rate Limiting**:
  - Filtro integrado de limitação de requisições por IP em rotas críticas de autenticação (mitigação de força bruta).
  - Proteção contra *Path Traversal* no armazenamento de arquivos locais.
  - Política de CORS configurável para integração segura com clientes Web.
- **Catálogo & Marketplace de SoundKits**:
  - Consulta pública de kits com paginação nativa e ordenação.
  - Filtros avançados por texto, tipo (`DRUM_KIT`, `SAMPLE_PACK`, `PRESET_BANK`, `MIDI_KIT`), faixas de preço e tags.
  - Consulta de tags populares do marketplace.
  - Criação, consulta e desativação (soft delete) de kits vinculados ao produtor logado.
- **Upload & Processamento de Mídia**:
  - Upload de pacotes de arquivos com suporte a arquivos comprimidos (`.zip`) e extração estruturada.
  - Upload e otimização de imagem de capa (`coverUrl`).
  - Upload de áudio de demonstração (`demo`) com extração de duração e geração de waveform para visualização de player no front-end.
  - Arquitetura de storage modular (armazenamento local protegido ou adaptadores em nuvem).

---

## 🛠️ Tecnologias Utilizadas

- **Linguagem:** Java 21 (LTS)
- **Framework:** Spring Boot 4.1.1
  - *Spring Web* (REST APIs)
  - *Spring Security* (Proteção de rotas e filtros)
  - *Spring Data MongoDB* (Persistência NoSQL e paginação)
  - *Spring Validation* (Validação de DTOs com Jakarta Bean Validation)
  - *Spring Mail* (Envio assíncrono de e-mails via SMTP/Mailtrap)
- **Banco de Dados:** MongoDB
- **Autenticação:** Auth0 Java JWT (HMAC-256)
- **Documentação Interativa:** SpringDoc OpenAPI 3.x (Swagger UI)
- **Produtividade & Utilitários:** Lombok
- **Build Tool:** Gradle
- **Containerização:** Docker e Docker Compose

---

## 🚀 Pré-requisitos e Instalação

### Pré-requisitos

- [Git](https://git-scm.com/)
- [Java JDK 21](https://adoptium.net/) ou superior
- [Docker](https://www.docker.com/) e Docker Compose

### Passo a Passo

1. **Clone o repositório:**
   ```bash
   git clone https://github.com/seu-usuario/sonic-lab-back-end.git
   cd sonic-lab-back-end
   ```

2. **Suba o banco de dados via Docker:**
   O projeto já contém um arquivo `docker-compose.yml` pré-configurado com MongoDB e Mongo Express:
   ```bash
   docker compose up -d
   ```
   * O MongoDB estará disponível em `localhost:27017`.
   * O painel web do Mongo Express estará em `http://localhost:8081`.

3. **Execute o projeto com o Gradle Wrapper:**
   - **Linux / macOS:**
     ```bash
     ./gradlew bootRun
     ```
   - **Windows:**
     ```powershell
     .\gradlew.bat bootRun
     ```

A API estará acessível em `http://localhost:8080`.

---

## ⚙️ Variáveis de Ambiente e Configuração

O projeto adota o padrão **12-Factor App**, permitindo que todas as propriedades sejam injetadas via variáveis de ambiente, mantendo valores padrão seguros e prontos para desenvolvimento local.

Para customizar o ambiente, copie o arquivo [`.env.example`](file:///.env.example) para `.env`:

```bash
cp .env.example .env
```

> ⚠️ **Atenção:** Arquivos `.env` contêm segredos e estão devidamente ignorados no `.gitignore`. **Nunca commite chaves de produção no repositório.**

### Tabela de Parâmetros

| Variável | Padrão (Dev Local) | Descrição |
| :--- | :--- | :--- |
| `PORT` | `8080` | Porta onde o servidor HTTP responderá. |
| `MONGODB_URI` | *Montada automaticamente* | URI completa de conexão (ex: MongoDB Atlas). Se informada, tem precedência. |
| `MONGODB_HOST` | `localhost` | Host do banco MongoDB. |
| `MONGODB_PORT` | `27017` | Porta do banco MongoDB. |
| `MONGODB_DATABASE` | `soniclab_db` | Nome do banco de dados da aplicação. |
| `MONGODB_USERNAME` | `admin` | Usuário do MongoDB (alinhado ao `docker-compose.yml`). |
| `MONGODB_PASSWORD` | `password` | Senha do MongoDB. |
| `JWT_SECRET` | `soniclab-super-secret-key...` | Chave secreta para assinatura dos tokens JWT. **Altere em produção!** |
| `JWT_EXPIRATION_HOURS`| `24` | Tempo de validade do token de acesso em horas. |
| `JWT_ISSUER` | `sonic-lab-api` | Identificador do emissor do token JWT. |
| `FRONTEND_URL` | `http://localhost:3000` | Origem permitida no CORS e base para links nos e-mails. |
| `MAIL_HOST` | `sandbox.smtp.mailtrap.io` | Servidor SMTP para disparo de e-mails. |
| `MAIL_PORT` | `587` | Porta do servidor SMTP. |
| `MAIL_USERNAME` | *(vazio)* | Usuário SMTP (ex: credencial Mailtrap ou SendGrid). |
| `MAIL_PASSWORD` | *(vazio)* | Senha SMTP. |
| `MAIL_FROM` | `SonicLab <noreply@soniclab.com>` | Remetente dos e-mails enviados. |
| `STORAGE_TYPE` | `local` | Provedor de armazenamento (`local`). |
| `STORAGE_LOCAL_DIR`| `./uploads` | Diretório no disco onde arquivos de upload são salvos. |
| `STORAGE_LOCAL_BASE_URL` | `http://localhost:8080/uploads/` | URL base para acesso público aos arquivos estáticos. |

---

## 📖 Documentação da API com Swagger

A API possui integração nativa com o **SpringDoc OpenAPI**. Mesmo com especificações e anotações customizadas em evolução contínua, os endpoints e esquemas estão disponíveis para exploração e testes interativos:

- **Swagger UI (Interface Interativa):**
  [http://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html)
- **Especificação OpenAPI em JSON:**
  [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs)

Para testar endpoints autenticados no Swagger, clique no botão **Authorize** e insira seu token no formato:
```text
Bearer <seu_token_aqui>
```

---

## 🔒 Autenticação e Autorização

### Formato do Token

A API utiliza tokens **JWT (JSON Web Token)** transmitidos no cabeçalho HTTP:

```http
Authorization: Bearer <seu_token_jwt>
```

### Perfis de Acesso

- `USER`: Perfil padrão atribuído no registro. Permite criar SoundKits, realizar uploads, listar os próprios produtos e gerenciar sua conta.
- `ADMIN`: Perfil com acesso privilegiado a rotas administrativas e monitoramento interno.

### Fluxo de Recuperação de Senha

1. O usuário requisita a recuperação em `POST /auth/forgot-password`.
2. O sistema gera um token temporário assinado com propósito específico (`purpose: password_reset`) com validade de 30 minutos e assinatura baseada no hash da senha atual.
3. Se o usuário alterar a senha com sucesso, o token é invalidado automaticamente para evitar reutilizações (*replay attack*).

### Rate Limiting

Endpoints sensíveis contam com proteção por IP em janela deslizante de 1 minuto:
- `POST /auth/login`: Máximo de 5 tentativas por minuto.
- `POST /auth/register`: Máximo de 5 tentativas por minuto.
- `POST /auth/forgot-password`: Máximo de 3 tentativas por minuto.

---

## 💡 Exemplos de Uso

### 1. Registrar Usuário
```bash
curl -X POST http://localhost:8080/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Producer Alpha",
    "email": "producer@example.com",
    "password": "Password123"
  }'
```
**Resposta (`201 Created`):**
```json
{
  "name": "Producer Alpha",
  "email": "producer@example.com"
}
```

### 2. Efetuar Login
```bash
curl -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "producer@example.com",
    "password": "Password123"
  }'
```
**Resposta (`200 OK`):**
```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

### 3. Listar SoundKits (Público com Filtros)
```bash
curl -X GET "http://localhost:8080/soundkits?type=DRUM_KIT&minPrice=0&maxPrice=150&page=0&size=10"
```
**Resposta (`200 OK`):**
```json
{
  "content": [
    {
      "id": "e4b2d184-7a1a-4d2c-8822-48a5c378e9b0",
      "title": "Midnight Trap Essentials",
      "description": "808s pesados, hi-hat rolls e caixas nítidas para Trap.",
      "price": 49.90,
      "type": "DRUM_KIT",
      "coverUrl": "http://localhost:8080/uploads/soundkits/covers/cover-sample.jpg",
      "tags": ["trap", "808", "hip-hop"],
      "creator": {
        "id": "c1f7b822-4a56-4c20-a651-7890bc45df12",
        "name": "Producer Alpha"
      }
    }
  ],
  "pageNumber": 0,
  "pageSize": 10,
  "totalElements": 1,
  "totalPages": 1,
  "isLast": true
}
```

### 4. Criar SoundKit (Autenticado)
```bash
curl -X POST http://localhost:8080/soundkits \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <seu_token_jwt>" \
  -d '{
    "title": "Analog Synth Presets Vol. 1",
    "description": "Presets para sintetizadores com sonoridade vintage dos anos 80.",
    "price": 35.00,
    "type": "PRESET_BANK",
    "coverUrl": "http://localhost:8080/uploads/soundkits/covers/synth-cover.png",
    "fileUrl": "http://localhost:8080/uploads/soundkits/packages/analog-synth.zip",
    "tags": ["synthwave", "analog", "vintage"],
    "specifications": {
      "Formato": "Serum Presets",
      "Tamanho": "150 MB"
    }
  }'
```

---

## ⚠️ Tratamento de Erros

A API possui um interceptador global de exceções (`@RestControllerAdvice`) que padroniza todas as respostas de erro no seguinte formato:

```json
{
  "status": 400,
  "error": "Validation Error",
  "message": "Dados inválidos fornecidos.",
  "details": {
    "password": "A senha deve conter pelo menos uma letra e um número",
    "title": "O título é obrigatório"
  }
}
```

### Principais Códigos de Status

| Código | Significado | Situação |
| :--- | :--- | :--- |
| `200 OK` | Sucesso | Requisição processada com êxito. |
| `201 Created` | Criado | Recurso criado (retorna cabeçalho `Location`). |
| `204 No Content` | Sem Conteúdo | Remoção ou desativação bem-sucedida. |
| `400 Bad Request` | Requisição Inválida | Erros de validação nos dados enviados ou token corrompido. |
| `401 Unauthorized`| Não Autenticado | Credenciais inválidas ou token ausente/expirado. |
| `403 Forbidden` | Proibido | Usuário autenticado não possui permissão para acessar o recurso. |
| `404 Not Found` | Não Encontrado | Recurso solicitado não existe. |
| `409 Conflict` | Conflito | Tentativa de cadastro com e-mail já existente. |
| `429 Too Many Requests` | Limite Excedido | Excedido o limite de requisições do Rate Limiter. |
| `500 Internal Server Error` | Erro do Servidor | Falha inesperada durante o processamento. |

---

## 📄 Licença e Contribuição

### Licença

Este projeto é distribuído sob os termos da licença **GNU General Public License v3.0**. Consulte o arquivo [`LICENSE`](file:///LICENSE) para obter detalhes completos.

### Contribuição

Contribuições são bem-vindas! Para colaborar:

1. Faça um **Fork** do projeto.
2. Crie uma branch para sua funcionalidade ou correção:
   ```bash
   git checkout -b feat/minha-nova-funcionalidade
   ```
3. Realize seus commits seguindo boas práticas e mensagens claras:
   ```bash
   git commit -m "feat: adiciona suporte a multipart upload em nuvem"
   ```
4. Envie sua branch para o repositório remoto:
   ```bash
   git push origin feat/minha-nova-funcionalidade
   ```
5. Abra um **Pull Request** detalhando as alterações propostas.
