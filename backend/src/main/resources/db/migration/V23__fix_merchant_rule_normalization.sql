-- V22: Fix merchant rule normalized_name values created before canonical normalization was stable.
--
-- Root cause: early USER rules stored the raw (mixed-case, punctuation-rich) description as
-- normalized_name. The current canonicalize() function lowercases, strips Brazilian amounts,
-- long digit runs, and punctuation before storing. The mismatch means findUserRuleByNormalizedName
-- (LOWER(r.normalizedName) = LOWER(:name)) never matches the old rules when a new import arrives
-- with a fresh canonical key.
--
-- Three-step fix:
--   1. Delete orphaned USER merchant_aliases: findAllGlobal() filters WHERE user IS NULL, so
--      aliases pointing at USER rules are never consulted during normalization.
--   2. Delete old-style rules that have a canonical counterpart for the same user + merchant:
--      the canonical rule (created by learnRule() on import confirmation) is authoritative.
--   3. Re-canonicalize any remaining old-style rules that have no canonical counterpart,
--      using the same regex chain as MerchantNormalizationService.canonicalize().

-- Step 1: orphaned USER merchant_aliases
DELETE FROM merchant_aliases ma
USING merchant_rules mr
WHERE ma.merchant_rule_id = mr.id
  AND mr.user_id IS NOT NULL;

-- Step 2: old-style rules superseded by a canonical rule for the same merchant + user
DELETE FROM merchant_rules mr_old
WHERE mr_old.created_by = 'USER'
  AND mr_old.user_id IS NOT NULL
  AND mr_old.normalized_name != LOWER(mr_old.normalized_name)
  AND EXISTS (
    SELECT 1 FROM merchant_rules mr_new
    WHERE mr_new.user_id  = mr_old.user_id
      AND mr_new.created_by = 'USER'
      AND mr_new.id != mr_old.id
      AND LOWER(mr_new.merchant_name) = LOWER(mr_old.merchant_name)
      AND mr_new.normalized_name = LOWER(mr_new.normalized_name)
  );

-- Step 3: re-canonicalize remaining old-style rules (no canonical counterpart exists yet)
-- Replicates canonicalize(): LOWER → strip bullet → strip BR amounts → strip 4+ digit runs
-- → strip non-word punctuation → collapse whitespace → trim
UPDATE merchant_rules
SET normalized_name = TRIM(
    REGEXP_REPLACE(
      REGEXP_REPLACE(
        REGEXP_REPLACE(
          REGEXP_REPLACE(
            LOWER(REPLACE(normalized_name, '•', ' ')),
            '[0-9]{1,3}(\.[0-9]{3})*,[0-9]{2}', ' ', 'g'),
          '\m[0-9]{4,}\M', ' ', 'g'),
        '[^[:alpha:][:digit:][:space:]-]', ' ', 'g'),
      '\s+', ' ', 'g'))
WHERE created_by = 'USER'
  AND user_id IS NOT NULL
  AND normalized_name != LOWER(normalized_name);
