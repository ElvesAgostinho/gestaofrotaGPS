# Regra de testes entre fases

Só se avança para a fase seguinte quando a fase atual está **a funcionar, testada
em profundidade e sem erros conhecidos**.

Antes de fechar uma fase:

1. **Testes automatizados** — unitários e de integração para a lógica e os
   endpoints novos. `mvn test` (backend) tem de passar a 100%.
2. **Verificação em execução real** — arrancar o backend e o cliente, exercitar
   os fluxos novos manualmente (não só os testes).
3. **Sem regressões** — os testes das fases anteriores continuam a passar.
4. **Erros tratados** — todos os caminhos de erro devolvem mensagem em português,
   nunca um erro técnico.
5. **Documentação** — README/ARCHITECTURE/API atualizados com o que mudou.
6. **Migrações** — Flyway aplica de forma limpa numa BD vazia e o esquema é
   coerente com as entidades JPA.

Só depois disto se começa a fase seguinte.
