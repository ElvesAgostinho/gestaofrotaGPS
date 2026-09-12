import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// App web desktop-first do AutoCare.
// Em desenvolvimento, /api é reencaminhado para o backend Spring Boot (porta 8080).
import { readFileSync } from 'node:fs';

const versao = (JSON.parse(readFileSync('./package.json', 'utf-8')) as { version: string }).version;

export default defineConfig({
  define: { __VERSAO__: JSON.stringify(versao) },
  plugins: [react()],
  server: {
    /*
     * Escutar em todas as interfaces, e nao so no loopback IPv6.
     *
     * Por omissao o Vite fica so em ::1. O Edge (e o Chrome, conforme a
     * configuracao da rede) resolvem `localhost` para 127.0.0.1 -- IPv4 --,
     * onde nao esta ninguem, e recusam a ligacao. O site parecia estar em
     * baixo quando na verdade estava a responder no endereco errado.
     *
     * Com isto tambem se abre do telemovel, pelo IP da maquina na rede.
     */
    host: true,
    port: 5173,
    proxy: {
      // 127.0.0.1 e nao `localhost`: o proxy nao pode depender de qual dos
      // dois loopbacks o sistema resolve primeiro.
      '/api': { target: 'http://127.0.0.1:8080', changeOrigin: true },
    },
  },
  build: {
    outDir: 'dist',
    sourcemap: false,
    rollupOptions: {
      output: {
        // Separar as bibliotecas do código da aplicação: elas mudam poucas
        // vezes, por isso o browser reaproveita-as entre versões em vez de
        // voltar a descarregar tudo a cada lançamento.
        manualChunks: {
          react: ['react', 'react-dom', 'react-router-dom'],
          mantine: ['@mantine/core', '@mantine/hooks', '@mantine/form', '@mantine/notifications'],
        },
      },
    },
  },
});
