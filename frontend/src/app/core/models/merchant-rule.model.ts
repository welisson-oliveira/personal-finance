export interface MerchantRule {
  id: string;
  merchantName: string;
  normalizedName: string;
  type?: string;
  categoryId?: string;
  categoryName?: string;
  subcategory?: string;
  expenseType: string;
  investmentDirection?: string;
  ignored: boolean;
  confidence: number;
  createdBy: string;
  global: boolean;
}

export interface CreateMerchantRuleRequest {
  merchantName: string;
  type?: string | null;
  categoryId?: string | null;
  subcategory?: string | null;
  expenseType?: string | null;
  investmentDirection?: string | null;
  ignored: boolean;
}
