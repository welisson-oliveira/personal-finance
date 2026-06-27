INSERT INTO merchant_rules (merchant_name, normalized_name, category_id, subcategory, expense_type, confidence, created_by)
SELECT 'Nagumo', 'Nagumo', id, 'Supermercado', 'ESSENTIAL', 100, 'SYSTEM' FROM categories WHERE name = 'Alimentação';

INSERT INTO merchant_rules (merchant_name, normalized_name, category_id, subcategory, expense_type, confidence, created_by)
SELECT 'Carrefour', 'Carrefour', id, 'Supermercado', 'ESSENTIAL', 100, 'SYSTEM' FROM categories WHERE name = 'Alimentação';

INSERT INTO merchant_rules (merchant_name, normalized_name, category_id, subcategory, expense_type, confidence, created_by)
SELECT 'Hirota', 'Hirota', id, 'Supermercado', 'ESSENTIAL', 100, 'SYSTEM' FROM categories WHERE name = 'Alimentação';

INSERT INTO merchant_rules (merchant_name, normalized_name, category_id, subcategory, expense_type, confidence, created_by)
SELECT 'iFood', 'iFood', id, 'Delivery', 'NON_ESSENTIAL', 100, 'SYSTEM' FROM categories WHERE name = 'Alimentação';

INSERT INTO merchant_rules (merchant_name, normalized_name, category_id, subcategory, expense_type, confidence, created_by)
SELECT 'Raia Drogasil', 'Raia Drogasil', id, 'Farmácia', 'ESSENTIAL', 100, 'SYSTEM' FROM categories WHERE name = 'Saúde';

INSERT INTO merchant_rules (merchant_name, normalized_name, category_id, subcategory, expense_type, confidence, created_by)
SELECT 'Farmaefarma', 'Farmaefarma', id, 'Farmácia', 'ESSENTIAL', 100, 'SYSTEM' FROM categories WHERE name = 'Saúde';

INSERT INTO merchant_rules (merchant_name, normalized_name, category_id, subcategory, expense_type, confidence, created_by)
SELECT 'Drogaria São Paulo', 'Drogaria São Paulo', id, 'Farmácia', 'ESSENTIAL', 100, 'SYSTEM' FROM categories WHERE name = 'Saúde';

INSERT INTO merchant_rules (merchant_name, normalized_name, category_id, subcategory, expense_type, confidence, created_by)
SELECT 'Wellhub', 'Wellhub', id, 'Academia', 'ESSENTIAL', 100, 'SYSTEM' FROM categories WHERE name = 'Saúde';

INSERT INTO merchant_rules (merchant_name, normalized_name, category_id, subcategory, expense_type, confidence, created_by)
SELECT 'Cobasi', 'Cobasi', id, 'Pet Shop', 'ESSENTIAL', 100, 'SYSTEM' FROM categories WHERE name = 'Pets';

INSERT INTO merchant_rules (merchant_name, normalized_name, category_id, subcategory, expense_type, confidence, created_by)
SELECT 'Poli Pet', 'Poli Pet', id, 'Pet Shop', 'ESSENTIAL', 100, 'SYSTEM' FROM categories WHERE name = 'Pets';

INSERT INTO merchant_rules (merchant_name, normalized_name, category_id, subcategory, expense_type, confidence, created_by)
SELECT 'Canny Pet', 'Canny Pet', id, 'Pet Shop', 'ESSENTIAL', 100, 'SYSTEM' FROM categories WHERE name = 'Pets';

INSERT INTO merchant_rules (merchant_name, normalized_name, category_id, subcategory, expense_type, confidence, created_by)
SELECT 'Uber', 'Uber', id, 'Aplicativo', 'NON_ESSENTIAL', 100, 'SYSTEM' FROM categories WHERE name = 'Transporte';

INSERT INTO merchant_rules (merchant_name, normalized_name, category_id, subcategory, expense_type, confidence, created_by)
SELECT 'NuTag', 'NuTag', id, 'Pedágio', 'ESSENTIAL', 100, 'SYSTEM' FROM categories WHERE name = 'Transporte';

INSERT INTO merchant_rules (merchant_name, normalized_name, category_id, subcategory, expense_type, confidence, created_by)
SELECT 'Auto Posto', 'Auto Posto', id, 'Combustível', 'ESSENTIAL', 100, 'SYSTEM' FROM categories WHERE name = 'Transporte';

INSERT INTO merchant_rules (merchant_name, normalized_name, category_id, subcategory, expense_type, confidence, created_by)
SELECT 'Estacionamento', 'Estacionamento', id, 'Estacionamento', 'NON_ESSENTIAL', 100, 'SYSTEM' FROM categories WHERE name = 'Transporte';

INSERT INTO merchant_rules (merchant_name, normalized_name, category_id, subcategory, expense_type, confidence, created_by)
SELECT 'SABESP', 'SABESP', id, 'Água', 'ESSENTIAL', 100, 'SYSTEM' FROM categories WHERE name = 'Contas';

INSERT INTO merchant_rules (merchant_name, normalized_name, category_id, subcategory, expense_type, confidence, created_by)
SELECT 'Conta Vivo', 'Conta Vivo', id, 'Telefone', 'ESSENTIAL', 100, 'SYSTEM' FROM categories WHERE name = 'Contas';

INSERT INTO merchant_rules (merchant_name, normalized_name, category_id, subcategory, expense_type, confidence, created_by)
SELECT 'PREF MUN', 'PREF MUN', id, 'Impostos', 'ESSENTIAL', 100, 'SYSTEM' FROM categories WHERE name = 'Contas';

INSERT INTO merchant_rules (merchant_name, normalized_name, category_id, subcategory, expense_type, confidence, created_by)
SELECT 'Amazon', 'Amazon', id, 'E-commerce', 'NON_ESSENTIAL', 100, 'SYSTEM' FROM categories WHERE name = 'Compras Online';

INSERT INTO merchant_rules (merchant_name, normalized_name, category_id, subcategory, expense_type, confidence, created_by)
SELECT 'Mercado Livre', 'Mercado Livre', id, 'E-commerce', 'NON_ESSENTIAL', 100, 'SYSTEM' FROM categories WHERE name = 'Compras Online';

INSERT INTO merchant_rules (merchant_name, normalized_name, category_id, subcategory, expense_type, confidence, created_by)
SELECT 'Magazine Torra Torra', 'Magazine Torra Torra', id, 'Vestuário', 'NON_ESSENTIAL', 100, 'SYSTEM' FROM categories WHERE name = 'Compras';

INSERT INTO merchant_rules (merchant_name, normalized_name, category_id, subcategory, expense_type, confidence, created_by)
SELECT 'Lojas Americanas', 'Lojas Americanas', id, 'Varejo', 'NON_ESSENTIAL', 100, 'SYSTEM' FROM categories WHERE name = 'Compras';

INSERT INTO merchant_rules (merchant_name, normalized_name, category_id, subcategory, expense_type, confidence, created_by)
SELECT 'Google YouTube', 'Google YouTube', id, 'Streaming', 'NON_ESSENTIAL', 100, 'SYSTEM' FROM categories WHERE name = 'Entretenimento';

INSERT INTO merchant_rules (merchant_name, normalized_name, category_id, subcategory, expense_type, confidence, created_by)
SELECT 'Amazon Prime', 'Amazon Prime', id, 'Streaming', 'NON_ESSENTIAL', 100, 'SYSTEM' FROM categories WHERE name = 'Entretenimento';

INSERT INTO merchant_rules (merchant_name, normalized_name, category_id, subcategory, expense_type, confidence, created_by)
SELECT 'PlayStation', 'PlayStation', id, 'Games', 'NON_ESSENTIAL', 100, 'SYSTEM' FROM categories WHERE name = 'Entretenimento';

INSERT INTO merchant_rules (merchant_name, normalized_name, category_id, subcategory, expense_type, confidence, created_by)
SELECT 'Anthropic', 'Anthropic', id, 'Assinatura', 'NON_ESSENTIAL', 100, 'SYSTEM' FROM categories WHERE name = 'Tecnologia';

INSERT INTO merchant_rules (merchant_name, normalized_name, category_id, subcategory, expense_type, confidence, created_by)
SELECT 'Barbearia', 'Barbearia', id, 'Cabelo', 'NON_ESSENTIAL', 100, 'SYSTEM' FROM categories WHERE name = 'Pessoal';

INSERT INTO merchant_rules (merchant_name, normalized_name, category_id, subcategory, expense_type, confidence, created_by)
SELECT 'Café', 'Café', id, 'Café', 'NON_ESSENTIAL', 100, 'SYSTEM' FROM categories WHERE name = 'Alimentação';

-- Aliases for Amazon
INSERT INTO merchant_aliases (merchant_rule_id, alias)
SELECT id, 'AmazonMktplc' FROM merchant_rules WHERE normalized_name = 'Amazon' AND user_id IS NULL;

INSERT INTO merchant_aliases (merchant_rule_id, alias)
SELECT id, 'Amazon Marketplace' FROM merchant_rules WHERE normalized_name = 'Amazon' AND user_id IS NULL;

INSERT INTO merchant_aliases (merchant_rule_id, alias)
SELECT id, 'Amazonmktplc' FROM merchant_rules WHERE normalized_name = 'Amazon' AND user_id IS NULL;

-- Aliases for iFood
INSERT INTO merchant_aliases (merchant_rule_id, alias)
SELECT id, 'iFood - NuPay' FROM merchant_rules WHERE normalized_name = 'iFood' AND user_id IS NULL;

INSERT INTO merchant_aliases (merchant_rule_id, alias)
SELECT id, 'IFood' FROM merchant_rules WHERE normalized_name = 'iFood' AND user_id IS NULL;

-- Aliases for Anthropic
INSERT INTO merchant_aliases (merchant_rule_id, alias)
SELECT id, 'Anthropic* Claude Sub' FROM merchant_rules WHERE normalized_name = 'Anthropic' AND user_id IS NULL;

INSERT INTO merchant_aliases (merchant_rule_id, alias)
SELECT id, 'ANTHROPIC' FROM merchant_rules WHERE normalized_name = 'Anthropic' AND user_id IS NULL;

-- Aliases for Raia Drogasil
INSERT INTO merchant_aliases (merchant_rule_id, alias)
SELECT id, 'Droga Raia' FROM merchant_rules WHERE normalized_name = 'Raia Drogasil' AND user_id IS NULL;

INSERT INTO merchant_aliases (merchant_rule_id, alias)
SELECT id, 'Drogasil' FROM merchant_rules WHERE normalized_name = 'Raia Drogasil' AND user_id IS NULL;

-- Aliases for Uber
INSERT INTO merchant_aliases (merchant_rule_id, alias)
SELECT id, 'Uber *' FROM merchant_rules WHERE normalized_name = 'Uber' AND user_id IS NULL;
