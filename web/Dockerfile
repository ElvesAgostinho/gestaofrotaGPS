# A aplicação web: compila-se com Node e serve-se com Nginx.
#
# A imagem final leva só os ficheiros estáticos e o Nginx — sem Node, sem
# código-fonte, sem node_modules. O Nginx também reencaminha /api para o
# backend, por isso a web e a API ficam no mesmo endereço e não há CORS a
# configurar no navegador do cliente.

# ---- Compilação ------------------------------------------------------------
FROM node:20-alpine AS build
WORKDIR /build

# As dependências mudam menos do que o código: camada à parte.
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund

COPY . .
RUN npm run build

# ---- Execução --------------------------------------------------------------
FROM nginx:1.27-alpine

# Template: o Nginx substitui ${API_UPSTREAM} ao arrancar.
COPY nginx.conf /etc/nginx/templates/default.conf.template
ENV API_UPSTREAM=api:8080
COPY --from=build /build/dist /usr/share/nginx/html

EXPOSE 80

HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
    CMD wget -qO- http://localhost/ >/dev/null || exit 1
