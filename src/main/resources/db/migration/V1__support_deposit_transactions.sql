DO $$
BEGIN
    IF to_regclass('public.transactions') IS NOT NULL THEN
        ALTER TABLE transactions
            ALTER COLUMN sender_account_id DROP NOT NULL;

        ALTER TABLE transactions
            ADD COLUMN IF NOT EXISTS transaction_type varchar(255) NOT NULL DEFAULT 'TRANSFER';

        ALTER TABLE transactions
            ADD COLUMN IF NOT EXISTS deposit_idempotency_key varchar(255);

        IF NOT EXISTS (
            SELECT 1
            FROM pg_constraint
            WHERE conname = 'uk_transactions_deposit_idempotency'
        ) THEN
            ALTER TABLE transactions
                ADD CONSTRAINT uk_transactions_deposit_idempotency
                UNIQUE (receiver_account_id, deposit_idempotency_key);
        END IF;
    END IF;
END
$$;
