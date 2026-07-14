# Personal Finance --- Visão do Produto

## Objetivo

A aplicação **não tem como objetivo reproduzir um extrato bancário** ou
servir como uma ferramenta de conciliação financeira.

Seu propósito é transformar movimentações financeiras em informações
úteis para tomada de decisão, permitindo que o usuário compreenda **para
onde seu dinheiro está indo** e acompanhe sua evolução financeira
utilizando como referência a metodologia **50/30/20**.

> **Pergunta principal que o sistema deve responder**
>
> **"Como utilizei meu dinheiro este mês?"**
>
> e não
>
> **"Quanto dinheiro entrou e saiu da minha conta?"**

------------------------------------------------------------------------

# Filosofia da aplicação

O sistema deve funcionar como um **painel gerencial pessoal**.

-   Extratos bancários mostram movimentações.
-   O Personal Finance mostra comportamento financeiro.
-   O foco não é o banco.
-   O foco é o usuário.

------------------------------------------------------------------------

# Objetivos

O sistema deve permitir que o usuário:

-   Visualizar sua renda do mês.
-   Entender onde seu dinheiro foi utilizado.
-   Identificar gastos essenciais.
-   Identificar gastos não essenciais.
-   Acompanhar quanto foi investido.
-   Acompanhar sua evolução ao longo dos meses.
-   Verificar se está seguindo a regra financeira 50/30/20.
-   Tomar decisões para melhorar sua saúde financeira.

------------------------------------------------------------------------

# O que o sistema NÃO pretende ser

O sistema não deve:

-   Reproduzir exatamente o saldo do banco.
-   Substituir o extrato bancário.
-   Fazer conciliação bancária.
-   Mostrar todas as movimentações como receita ou despesa.

As movimentações bancárias são apenas a fonte de dados.

Após a importação, elas devem ser classificadas para gerar indicadores
financeiros.

------------------------------------------------------------------------

# Fluxo conceitual do dinheiro

``` text
Extrato Bancário
        │
        ▼
Importação
        │
        ▼
Classificação
        │
        ▼
Dashboard Financeiro
```

O dashboard nunca deve exibir simplesmente o que aconteceu no banco.

Ele deve mostrar **o significado financeiro** dessas movimentações.

------------------------------------------------------------------------

# Modelo Financeiro

## Entradas

Representam dinheiro que realmente aumentou o patrimônio disponível.

**Exemplos**

-   Salário
-   Freelancer
-   Venda de produtos
-   Bonificações
-   Rendimentos
-   PIX recebido de terceiros

**Não entram**

-   Transferências entre contas próprias
-   Resgates de investimentos
-   Reembolsos
-   Estornos

------------------------------------------------------------------------

## Despesas Essenciais (50%)

Gastos necessários para manter a vida.

Exemplos:

-   Aluguel
-   Água
-   Energia
-   Internet
-   Supermercado
-   Transporte
-   Combustível
-   Plano de saúde
-   Medicamentos

------------------------------------------------------------------------

## Despesas Não Essenciais (30%)

Gastos ligados ao estilo de vida.

Exemplos:

-   Delivery
-   Restaurantes
-   Cinema
-   Streaming
-   Viagens
-   Jogos
-   Roupas
-   Lazer

------------------------------------------------------------------------

## Investimentos (20%)

Representam dinheiro destinado à construção de patrimônio.

Exemplos:

-   RDB
-   CDB
-   Tesouro Direto
-   ETFs
-   Fundos
-   Ações

> Investimento não é despesa.
>
> É uma realocação de patrimônio.

------------------------------------------------------------------------

## Resgates

Representam patrimônio voltando para liquidez.

Não devem ser considerados renda.

------------------------------------------------------------------------

## Transferências Internas

Transferências entre contas do próprio usuário.

Nunca devem aumentar:

-   Receita
-   Despesa

------------------------------------------------------------------------

## Reembolsos

Devem aparecer separados da renda.

Não representam geração de riqueza.

------------------------------------------------------------------------

# Dashboard

A Home deve responder quatro perguntas.

## 1. Quanto dinheiro entrou?

Exibir:

-   Entradas do mês
-   Reembolsos
-   Receita disponível

------------------------------------------------------------------------

## 2. Para onde o dinheiro foi?

Exibir:

-   Despesas Essenciais
-   Despesas Não Essenciais
-   Investimentos

Este é o principal bloco da aplicação.

------------------------------------------------------------------------

## 3. Estou seguindo o método 50/30/20?

Apresentar:

-   Percentuais
-   Valores
-   Gráfico

Distribuição ideal:

-   **50%** Necessidades
-   **30%** Desejos
-   **20%** Investimentos

------------------------------------------------------------------------

## 4. Quanto sobrou?

Exibir:

-   Resultado do mês

Evitar utilizar o termo **Saldo**, pois normalmente ele é associado ao
saldo bancário.

------------------------------------------------------------------------

# Sugestão de nomenclatura

  Atual           Sugestão
  --------------- -----------------------------
  Receita Bruta   Entradas do mês
  Receita Real    Receita disponível
  Saldo           Resultado do mês
  Investido       Aplicado em investimentos
  Resgatado       Resgatado dos investimentos

------------------------------------------------------------------------

# Princípios de Produto

Toda funcionalidade deve responder:

-   Ajuda o usuário a entender para onde o dinheiro foi?
-   Ajuda o usuário a tomar melhores decisões financeiras?
-   Aproxima o usuário da regra 50/30/20?
-   Evita dupla contagem de dinheiro?

Se a resposta for **não**, a funcionalidade deve ser reavaliada.

------------------------------------------------------------------------

# Prioridades

1.  Classificação automática das movimentações.
2.  Correção manual das classificações.
3.  Dashboard financeiro.
4.  Indicadores 50/30/20.
5.  Relatórios mensais.
6.  Evolução histórica.

------------------------------------------------------------------------

# Missão

> Ajudar o usuário a desenvolver uma relação mais consciente com o
> próprio dinheiro, transformando movimentações bancárias em informações
> simples, organizadas e acionáveis, permitindo acompanhar a
> distribuição da renda entre necessidades, desejos e construção de
> patrimônio através da metodologia 50/30/20.

------------------------------------------------------------------------

# Prompt para IA

Você está atuando como **Product Owner**, **UX Designer** e **Arquiteto
de Software** da aplicação **Personal Finance**.

Antes de sugerir qualquer funcionalidade, considere que o objetivo do
sistema **não é reproduzir um extrato bancário**, mas transformar
movimentações financeiras em indicadores gerenciais que ajudem o usuário
a entender **para onde seu dinheiro está indo**.

Toda funcionalidade deve reforçar a metodologia **50/30/20**,
priorizando a classificação das movimentações em:

-   Entradas
-   Despesas Essenciais
-   Despesas Não Essenciais
-   Investimentos
-   Transferências Internas
-   Resgates
-   Reembolsos

Ao propor soluções:

-   Priorize clareza e simplicidade.
-   Evite replicar informações do banco sem gerar novos insights.
-   Explique como cada funcionalidade ajuda o usuário a tomar melhores
    decisões financeiras.
-   Preserve a filosofia do produto em todas as evoluções.
