package com.personalfinance.service;

import java.util.List;

/**
 * The starter category tree copied into each user's account on registration (Option 2: the user
 * owns and can edit/delete everything). Purely seed data — the classification intelligence works on
 * any category the user creates later; nothing here is hardcoded into the engine.
 */
public final class DefaultCategories {

  private DefaultCategories() {}

  /**
   * A top-level category with its icon, color and subcategory names (children inherit the color).
   */
  public record Group(String name, String icon, String color, List<String> children) {}

  public static final List<Group> TREE =
      List.of(
          new Group(
              "Receitas",
              "attach_money",
              "#2E7D32",
              List.of("Salário", "Renda Extra", "Rendimentos", "Outros")),
          new Group(
              "Moradia",
              "home",
              "#6D4C41",
              List.of("Aluguel/Financiamento", "Contas", "Manutenção", "Outros")),
          new Group(
              "Alimentação",
              "restaurant",
              "#43A047",
              List.of("Mercado", "Refeições", "Delivery", "Outros")),
          new Group(
              "Transporte",
              "directions_car",
              "#1565C0",
              List.of("Combustível", "App/Público", "Manutenção/Seguro", "Outros")),
          new Group(
              "Saúde",
              "local_hospital",
              "#E53935",
              List.of("Plano de Saúde", "Farmácia", "Consultas/Exames", "Outros")),
          new Group(
              "Educação", "school", "#00897B", List.of("Cursos", "Escola/Faculdade", "Outros")),
          new Group(
              "Lazer",
              "sports_esports",
              "#FB8C00",
              List.of("Assinaturas", "Passeios", "Viagem", "Outros")),
          new Group(
              "Compras",
              "shopping_bag",
              "#E91E63",
              List.of("Vestuário", "Casa", "Eletrônicos", "Outros")),
          new Group("Impostos e Taxas", "account_balance", "#5D4037", List.of("Outros")),
          new Group("Investimentos", "trending_up", "#7CB342", List.of("Aplicações", "Resgates")),
          new Group(
              "Transferências", "swap_horiz", "#9E9E9E", List.of("Entre Contas", "Para Terceiros")),
          new Group("Outros", "category", "#616161", List.of("Não Classificado")));

  /**
   * Maps a legacy (flat V2) global category name to the top-level name in the new tree, so existing
   * transactions/rules can be remapped when a user is migrated to the owned tree.
   */
  public static String legacyTopLevel(String legacyName) {
    return switch (legacyName) {
      case "Alimentação" -> "Alimentação";
      case "Saúde" -> "Saúde";
      case "Transporte" -> "Transporte";
      case "Contas" -> "Moradia";
      case "Compras Online", "Compras", "Tecnologia" -> "Compras";
      case "Entretenimento" -> "Lazer";
      case "Investimentos" -> "Investimentos";
      case "Transferências" -> "Transferências";
      case "Pets", "Pessoal" -> "Outros";
      default -> "Outros";
    };
  }
}
