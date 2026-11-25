-- Split tender: a sale can be paid part cash, part M-PESA.
-- The M-PESA leg is derived as total_amount_cents - cash_amount_cents.

ALTER TABLE sale DROP CONSTRAINT IF EXISTS sale_payment_method_check;
ALTER TABLE sale ADD CONSTRAINT sale_payment_method_check
  CHECK (payment_method IN ('CASH','MPESA','CARD','SPLIT'));

ALTER TABLE sale ADD COLUMN cash_amount_cents BIGINT NOT NULL DEFAULT 0;

-- Cash can never exceed the sale total, and only SPLIT rows carry a cash leg.
ALTER TABLE sale ADD CONSTRAINT sale_split_shape
  CHECK (
    (payment_method = 'SPLIT'
       AND cash_amount_cents > 0
       AND cash_amount_cents < total_amount_cents)
    OR (payment_method <> 'SPLIT' AND cash_amount_cents = 0)
  );
