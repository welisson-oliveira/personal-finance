export interface MerchantRule {
  id: string;
  merchantName: string;
  normalizedName: string;
  categoryId?: string;
  categoryName?: string;
  subcategory?: string;
  expenseType: string;
  confidence: number;
  createdBy: string;
  global: boolean;
}

export interface CreateMerchantRuleRequest {
  merchantName: string;
  categoryId?: string | null;
  subcategory?: string | null;
  expenseType: string;
}
