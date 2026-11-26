# Sonic Lab Backend — Guia de Deploy em Produção

Este documento descreve como construir, configurar e publicar o **Sonic Lab Backend** em produção utilizando **Render** (Docker), **MongoDB Atlas**, **AWS S3** e integração com o frontend na **Vercel**.

---

## 1. Pré-requisitos

- **Java 21** e **Gradle Wrapper** (incluído no repositório) para build local.
- **Docker** (opcional localmente para testar a imagem antes do deploy).
- Conta no **MongoDB Atlas** (Cluster M0 gratuito ou superior).
- Conta na **AWS** com um bucket S3 e usuário/credenciais IAM dedicadas.
- Conta no **Render** conectada ao repositório GitHub do backend.
- Conta no **Mailtrap** (Sandbox SMTP) ou outro provedor SMTP compatível.
- URL fixa do frontend publicado na **Vercel** (para configuração estrita de CORS).

---

## 2. Variáveis de Ambiente

| Variável | Obrigatória | Descrição |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | Sim (prod) | Defina como `prod` em produção para ativar `application-prod.properties` (desativa fallbacks locais de dev). |
| `PORT` | Não | Porta HTTP da aplicação (default: `8080`). No Render, é injetada automaticamente. |
| `MONGODB_URI` | Sim (prod) | URI completa do MongoDB Atlas (`mongodb+srv://<usuario>:<senha>@<cluster>.mongodb.net/soniclab_db?retryWrites=true&w=majority`). |
| `MONGODB_DATABASE` | Não | Nome do banco MongoDB (default: `soniclab_db`). |
| `JWT_SECRET` | Sim (prod) | Chave HMAC-SHA256 criptograficamente aleatória com >= 32 bytes (256 bits). Gere com `openssl rand -base64 64`. A aplicação falha no startup se ausente, fraca ou igual ao segredo de desenvolvimento. |
| `JWT_EXPIRATION_HOURS` | Não | Tempo de validade do token de acesso em horas (default: `24`). |
| `JWT_ISSUER` | Não | Emissor (`iss`) do JWT (default: `sonic-lab-api`). |
| `FRONTEND_URL` | Sim (prod) | Origem fixa permitida no CORS e usada nos links de e-mail (ex: `https://sonic-lab.vercel.app`). Não aceita `*` nem `localhost` em produção. |
| `STORAGE_TYPE` | Sim (prod) | Define a implementação de `FileStorageService`: `local` (dev) ou `s3` (prod). |
| `S3_BUCKET` | Sim (se `s3`) | Nome do bucket AWS S3. |
| `AWS_REGION` | Sim (se `s3`) | Região do bucket AWS S3 (ex: `us-east-2`). |
| `AWS_ACCESS_KEY_ID` | Sim (no Render) | Access Key ID de usuário IAM com permissão restrita ao bucket. Opcional caso use IAM Role. |
| `AWS_SECRET_ACCESS_KEY` | Sim (no Render) | Secret Access Key correspondente. Nunca registre em logs ou commits. |
| `S3_URL_DURATION_MINUTES` | Não | Validade (TTL) das Presigned URLs de download de pacotes privados em minutos (default: `15`, máx: 7 dias). |
| `S3_PUBLIC_BASE_URL` | Não | URL base customizada para arquivos públicos (ex: domínio CloudFront, se configurado). |
| `MAX_FILE_SIZE` | Não | Limite por arquivo no multipart (default: `100MB`). |
| `MAX_REQUEST_SIZE` | Não | Limite total da requisição multipart (default: `200MB` em prod/default). |
| `MAX_DEMO_BYTES` | Não | Limite em bytes para upload de áudio demo (default: `20971520` = 20 MB). |
| `PAGEABLE_DEFAULT_SIZE` | Não | Tamanho padrão de página na paginação (default: `50`). |
| `MAIL_HOST` | Não | Host SMTP (default: `sandbox.smtp.mailtrap.io`). |
| `MAIL_PORT` | Não | Porta SMTP (`587` padrão ou `2525` caso o provedor bloqueie a porta 587). |
| `MAIL_USERNAME` | Sim (se e-mail ativo) | Usuário SMTP do Mailtrap/provedor. |
| `MAIL_PASSWORD` | Sim (se e-mail ativo) | Senha SMTP do Mailtrap/provedor. |
| `MAIL_SSL_TRUST` | Não | Host confiável para TLS SMTP (default: `sandbox.smtp.mailtrap.io`). |
| `MAIL_FROM` | Não | Remetente exibido nos e-mails (default: `SonicLab <noreply@soniclab.com>`). |

---

## 3. Construção e Execução Local do Container Docker

### Build da imagem

```bash
docker build -t sonic-lab-backend:prod .
```

O `Dockerfile` usa build multi-stage:
1. **Stage `builder`** (`eclipse-temurin:21-jdk-jammy`): executa `./gradlew bootJar --no-daemon` com Gradle 9.7.1.
2. **Stage `runtime`** (`eclipse-temurin:21-jre-jammy`): imagem enxuta JRE 21 com usuário não-root (`soniclab`), suporte glibc para o binário FFmpeg embarcado no JAVE 3.5.0 e limites de heap adequados para containers de 512 MB (`-XX:MaxRAMPercentage=70.0`).

### Execução local em modo desenvolvimento (com MongoDB do Docker Compose)

1. Inicie o MongoDB local:
   ```bash
   docker compose up -d
   ```
2. Execute o container apontando para o host local:
   ```bash
   docker run --rm -p 8080:8080 \
     -e STORAGE_TYPE=local \
     -e MONGODB_HOST=host.docker.internal \
     sonic-lab-backend:prod
   ```
3. Verifique o health check:
   ```bash
   curl http://localhost:8080/health
   # {"status":"UP"}
   ```

### Execução local simulando modo produção (validação fail-fast)

```bash
docker run --rm -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e STORAGE_TYPE=s3 \
  -e S3_BUCKET=meu-bucket-teste \
  -e AWS_REGION=us-east-2 \
  -e JWT_SECRET="$(openssl rand -base64 64 | tr -d '\n')" \
  -e FRONTEND_URL=https://sonic-lab.vercel.app \
  -e MONGODB_URI="mongodb+srv://usuario:senha@cluster.mongodb.net/soniclab_db?retryWrites=true&w=majority" \
  sonic-lab-backend:prod
```

---

## 4. Configuração do MongoDB Atlas

1. Crie um cluster **M0 (Free)** no MongoDB Atlas.
2. Em **Database Access**, crie um usuário de banco dedicado com permissão `readWrite` no banco `soniclab_db`.
3. Em **Network Access**, adicione os IPs de saída do serviço Render (ou `0.0.0.0/0` temporariamente caso utilize IPs dinâmicos no plano gratuito do Render, mantendo autenticação forte e TLS obrigatório).
4. Copie a connection string `mongodb+srv://...` e configure-a em `MONGODB_URI`.
5. O banco iniciará vazio; os índices configurados nas entidades (`User.email` unique e `SoundKit.creatorId`) serão criados automaticamente.

---

## 5. Configuração do AWS S3 (Menor Privilégio)

A arquitetura separa estritamente objetos **públicos** e **privados** dentro do mesmo bucket:

- `soundkits/packages/*` → **Privados** (acesso exclusivo via Presigned URLs temporárias em `GET /soundkits/{id}/download`).
- `soundkits/covers/*` e `soundkits/demos/*` → **Públicos** (leitura direta pelo player/frontend sem autenticação).

### Passo 1 — Block Public Access

1. **Mantenha** o *Block Public Access* global da conta AWS habilitado.
2. No bucket do Sonic Lab:
   - Mantenha `Block public access to buckets and objects granted through new access control lists (ACLs)` = **ON**.
   - Mantenha `Block public access to buckets and objects granted through any access control lists (ACLs)` = **ON**.
   - Para permitir a Bucket Policy restrita apenas aos prefixos públicos, desmarque somente as duas opções referentes a *Bucket Policies* no bucket específico (não habilite ACLs públicas e não torne o bucket inteiro público).

### Passo 2 — Bucket Policy restrita aos prefixos públicos

Aplique a seguinte Bucket Policy substituindo `SEU_BUCKET`:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "AllowPublicReadOnlyCoversAndDemos",
      "Effect": "Allow",
      "Principal": "*",
      "Action": "s3:GetObject",
      "Resource": [
        "arn:aws:s3:::SEU_BUCKET/soundkits/covers/*",
        "arn:aws:s3:::SEU_BUCKET/soundkits/demos/*"
      ]
    }
  ]
}
```

> **Nota:** `soundkits/packages/*` não está listado nesta policy e permanecerá bloqueado para acesso anônimo (`403 Forbidden`), sendo acessível apenas via Presigned URLs assinadas pelo backend.

### Passo 3 — Política CORS do Bucket S3

Permita que o frontend na Vercel reproduza os áudios demo (ex.: WaveSurfer / Web Audio API) e carregue as capas diretamente do S3:

```json
[
  {
    "AllowedHeaders": ["*"],
    "AllowedMethods": ["GET", "HEAD"],
    "AllowedOrigins": ["https://SUA-URL-VERCEL.vercel.app"],
    "ExposeHeaders": ["ETag", "Content-Length", "Content-Type"],
    "MaxAgeSeconds": 3600
  }
]
```

### Passo 4 — Usuário IAM para o Backend

Crie um usuário IAM programático (ex: `soniclab-backend-s3`) com a política mínima abaixo e informe `AWS_ACCESS_KEY_ID` e `AWS_SECRET_ACCESS_KEY` no Render:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "SonicLabBackendBucketAccess",
      "Effect": "Allow",
      "Action": [
        "s3:PutObject",
        "s3:GetObject",
        "s3:DeleteObject",
        "s3:HeadObject"
      ],
      "Resource": "arn:aws:s3:::SEU_BUCKET/soundkits/*"
    }
  ]
}
```

---

## 6. Deploy no Render

1. No painel do Render, crie um novo **Web Service** conectado ao repositório GitHub do backend.
2. Em **Language / Runtime**, selecione **Docker** (o Render detectará o `Dockerfile` na raiz automaticamente).
3. Selecione o plano **Free** (ou superior).
4. Em **Health Check Path**, configure:
   ```
   /health
   ```
5. Em **Environment Variables**, cadastre todas as variáveis obrigatórias da seção 2 (`SPRING_PROFILES_ACTIVE=prod`, `MONGODB_URI`, `JWT_SECRET`, `FRONTEND_URL`, `STORAGE_TYPE=s3`, `S3_BUCKET`, `AWS_REGION`, `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`, além das credenciais SMTP do Mailtrap).
6. Inicie o deploy e acompanhe a aba **Logs**.

---

## 7. Health Check e Monitoramento de Logs

### Endpoint de Health Check

- Rota pública: `GET /health`
- Resposta esperada: `200 OK` com corpo `{"status":"UP"}`
- Não expõe credenciais, strings de conexão ou informações internas.

### Logs Esperados no Startup

Os logs são emitidos diretamente para `stdout`/`stderr`:
- `Inicializando Sonic Lab Backend (modo=production, storage.type=s3)`
- `Storage S3 configurado (bucket=..., region=...)`
- `Validação de segurança para produção concluída com sucesso (CORS origin=https://...)`

---

## 8. Diagnóstico de Falhas Comuns

| Sintoma | Causa Provável | Resolução |
|---|---|---|
| Startup aborta com `IllegalStateException: JWT_SECRET...` | `JWT_SECRET` ausente, igual ao segredo de dev, < 32 bytes ou com baixa entropia em produção. | Gere uma chave forte com `openssl rand -base64 64` e defina em `JWT_SECRET`. |
| Startup aborta com `FRONTEND_URL...` | `FRONTEND_URL` ausente, `*` ou `localhost` em produção. | Configure a URL exata da Vercel (ex: `https://sonic-lab.vercel.app`). |
| Erro de CORS no navegador | `FRONTEND_URL` diferente do domínio real acessado na Vercel (ex: `http` vs `https` ou barra final `/`). | Verifique o domínio exato no `Origin` do navegador e atualize `FRONTEND_URL`. |
| Timeout no upload de demo (`504` ou conexão encerrada) | Plano gratuito do Render tem timeout HTTP de 30s e RAM de 512 MB; transcodificação de WAV próximo a 20 MB pode exceder o tempo. | Envie demos já em MP3 ou arquivos WAV menores; mantenha `MAX_DEMO_BYTES` conservador (<= 20 MB). |
| Erro ao enviar e-mail de recuperação (`Connection timed out`) | Provedor de nuvem bloqueando saída na porta `587`. | Altere `MAIL_PORT=2525` (suportada pelo Mailtrap Sandbox). |
| Acesso negado (`403`) ao abrir capa ou demo no S3 | Bucket Policy restrita aos prefixos `soundkits/covers/*` e `soundkits/demos/*` ainda não aplicada ou Block Public Policy ativo no bucket. | Revise a seção 5 deste guia. |
| Primeiro acesso demora ~30-60 segundos | *Spin down* por inatividade de 15 minutos no plano gratuito do Render (*cold start*). | Comportamento normal do plano gratuito; requisições subsequentes respondem normalmente. |
