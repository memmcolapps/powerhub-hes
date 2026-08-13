-- 1. Create meter_integrations table if not exists
CREATE TABLE IF NOT EXISTS public.meter_integrations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    manufacturer VARCHAR(255) NOT NULL,
    model VARCHAR(255) NOT NULL,
    class VARCHAR(255) NOT NULL,
    category VARCHAR(255) NOT NULL,
    protocol VARCHAR(255) NOT NULL,
    authentication_type VARCHAR(255) NOT NULL,
    password VARCHAR(255),
    serial VARCHAR(100),
    multiplier VARCHAR(50),
    security_policy VARCHAR(100),
    auth_mechanism VARCHAR(100),
    encryption_key VARCHAR(255),
    master_key VARCHAR(255),
    global_broadcast_encryption_key VARCHAR(255),
    destination_address VARCHAR(255),
    client_id VARCHAR(100),
    description VARCHAR(1000),
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    status_reason VARCHAR(1000),
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW()
);

-- 2. Add organisation_id and meter_integration_id to public.meters table if not exists
ALTER TABLE public.meters ADD COLUMN IF NOT EXISTS organisation_id UUID;
ALTER TABLE public.meters ADD COLUMN IF NOT EXISTS meter_integration_id UUID;

-- 3. Populate organisation_id from org_id where organisation_id is NULL
UPDATE public.meters SET organisation_id = CAST(org_id AS UUID) WHERE organisation_id IS NULL AND org_id IS NOT NULL;

-- 4. Create and associate meter_integrations for existing meters
DO $$
DECLARE
    r RECORD;
    new_integration_id UUID;
    man_name VARCHAR;
BEGIN
    FOR r IN
        SELECT m.id AS meter_id, m.org_id, m.meter_category, m.meter_class, m.meter_manufacturer,
               s.meter_model, s.protocol, s.authentication, s.password, m.meter_number
        FROM public.meters m
        LEFT JOIN public.smart_meter_info s ON m.id = s.meter_id
        WHERE m.meter_integration_id IS NULL
    LOOP
        new_integration_id := gen_random_uuid();

        -- Get manufacturer name from UUID reference if present
        SELECT name INTO man_name FROM public.manufacturers WHERE id = r.meter_manufacturer;
        IF man_name IS NULL THEN
            man_name := 'GENERIC';
        END IF;

        INSERT INTO public.meter_integrations (
            id, manufacturer, model, class, category, protocol, authentication_type, password, serial, created_at, updated_at
        ) VALUES (
            new_integration_id,
            man_name,
            COALESCE(r.meter_model, 'GENERIC'),
            COALESCE(r.meter_class, 'GENERIC'),
            COALESCE(r.meter_category, 'GENERIC'),
            COALESCE(r.protocol, 'TCP'),
            COALESCE(r.authentication, 'NONE'),
            r.password,
            r.meter_number,
            NOW(),
            NOW()
        );

        UPDATE public.meters
        SET meter_integration_id = new_integration_id
        WHERE id = r.meter_id;
    END LOOP;
END $$;
