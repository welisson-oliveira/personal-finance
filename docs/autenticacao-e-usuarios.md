# Autenticação e usuários

Registro/login com JWT, proteção das rotas, e o perfil do usuário (incluindo o salário líquido mensal usado pelo dashboard).

## Backend

### Fluxo de segurança (`config/`)

- **`SecurityConfig`** — `@EnableWebSecurity`, sessão stateless, CSRF off, CORS de `CorsConfig`. Libera `/api/auth/**` e `/api/actuator/**`; todo o resto exige autenticação. Registra `JwtAuthFilter` antes do `UsernamePasswordAuthenticationFilter`; 401 no entry point.
- **`JwtAuthFilter`** — `OncePerRequestFilter`: lê `Authorization: Bearer`, extrai o email via `JwtService`, carrega o usuário via `UserService`, popula o `SecurityContext` quando o token é válido (segue silencioso se ausente/ inválido).
- **`JwtService`** — HS256: `generateToken`, `extractUsername`, `isTokenValid`, expiração. Chave em `app.jwt.secret` (`JWT_SECRET`), TTL em `app.jwt.expiration-ms` (`JWT_EXPIRATION_MS`, padrão 24h).
- **`CorsConfig`** — origem `http://localhost:4200`, métodos GET/POST/PUT/DELETE/OPTIONS/PATCH, credenciais true, escopo `/api/**`.
- **`ApplicationConfig`** — beans `BCryptPasswordEncoder` e `AuthenticationManager`.
- **`GlobalExceptionHandler`** — `@RestControllerAdvice`: `IllegalArgumentException`→400, `AccessDeniedException`→403, `AuthenticationException`→401, `NoResourceFoundException`→404, genérico→500. Corpo JSON `{error, message, status, timestamp}`.

### Endpoints

`AuthController` (`/api/auth`):

- `POST /register` — `RegisterRequest {name, email, password(@Size min 8)}` → cria usuário, retorna `AuthResponse {token, user}` (201).
- `POST /login` — `LoginRequest {email(@Email), password}` → autentica, retorna `AuthResponse` (200) ou 401.

`UserController` (`/api/users`):

- `GET /me` — perfil atual (`UserResponse {id, name, email, monthlyNetIncome, openingBalance, openingBalanceDate}`).
- `PUT /me` — `UpdateProfileRequest {monthlyNetIncome, openingBalance(@PositiveOrZero), openingBalanceDate}` → atualiza e retorna `UserResponse`.

### Service / entidade

- **`UserService`** `implements UserDetailsService`: `loadUserByUsername` por email; `register` (guarda contra email duplicado, BCrypt); `updateProfile` (`monthlyNetIncome`, `openingBalance`, `openingBalanceDate`).
- **`User`** (`users`) `implements UserDetails`: email (único), password (BCrypt), name, `monthlyNetIncome` (NUMERIC 19,2 — V8), `openingBalance` (NUMERIC 19,2) + `openingBalanceDate` (DATE) — saldo inicial de referência do Saldo Geral (V15), timestamps. Authorities vazias.

## Frontend

### `core/auth/`

- **`AuthService`** (`providedIn: 'root'`): `currentUser = signal<UserResponse|null>` iniciado do `localStorage` (`auth_user`); chaves `auth_token`/`auth_user`.
  - `login(req)` → `POST /api/auth/login` (armazena via `tap`); `register(req)` → `POST /api/auth/register`.
  - `logout()` limpa storage, zera o signal, navega `/auth/login`.
  - `getToken()`, `isAuthenticated()`, `getUser()`, `updateStoredUser(user)` (persiste + atualiza o signal — usado ao salvar Configurações).
- **`authInterceptor`** (functional `HttpInterceptorFn`) — injeta `AuthService` e adiciona `Authorization: Bearer <token>` quando há token. Registrado em `app.config.ts` via `provideHttpClient(withInterceptors([authInterceptor]))`.
- **`authGuard`** (functional `CanActivateFn`) — libera se autenticado, senão navega `/auth/login`. Aplicado ao shell (`LayoutComponent`), protegendo todas as rotas filhas.

### Telas

- **`feature/auth/login`** (`app-login`) — form reativo (email + password), `submit()` → `auth.login()` → `/dashboard`.
- **`feature/auth/register`** (`app-register`) — form reativo (name minLen 2, email, password minLen 6), `submit()` → `auth.register()` → `/dashboard`.
- **`feature/settings`** (`app-settings`) — carrega `monthlyNetIncome`, `openingBalance` e `openingBalanceDate` via `settings.service.getMe()`; card **"Orçamento"** (salário líquido, base do 50/30/20 e piso do salário previsto) + card **"Saldo inicial"** (saldo real da conta numa data de referência, para o Saldo Geral bater com a conta). `save()` valida salário ≥ 0 e exige a data quando há saldo informado, chama `updateProfile()` e faz `auth.updateStoredUser()`. Usa `CurrencyMaskDirective`. `settings.service`: `getMe()` → `GET /api/users/me`; `updateProfile(monthlyNetIncome, openingBalance, openingBalanceDate)` → `PUT /api/users/me`.

## Fluxo ponta-a-ponta

1. Registro/login → backend gera JWT → frontend guarda token+user (signal + localStorage).
2. Toda requisição autenticada leva `Authorization: Bearer` (interceptor). `JwtAuthFilter` valida e popula o `SecurityContext`.
3. Acesso a rota protegida sem token → `authGuard` redireciona para login.
4. Em Configurações, o salário líquido salvo alimenta o cálculo 50/30/20 e o salário previsto, e o saldo inicial semeia o Saldo Geral (ver [dashboard.md](./dashboard.md)).

## Onde mexer

- Novo campo de perfil → `User` (+ migration), `UserResponse`, `UpdateProfileRequest`, `UserService.updateProfile`, `settings.component`.
- Nova rota protegida → adicionar como filha do shell em `app.routes.ts` (já herda `authGuard`) + item em `navItems` do `LayoutComponent`.
- Regras de senha/registro → `RegisterRequest` (validações) + `UserService.register`.

## Testes relevantes

`AuthControllerTest` (register 201+token, login 200/401, rota protegida 401 sem token / 200 com token), `UserServiceTest` (loadUserByUsername, register novo/duplicado).
