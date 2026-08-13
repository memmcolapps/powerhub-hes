-- 1. Create meter_integrations table if not exists
CREATE TABLE IF NOT EXISTS public.meter_integrations (
    id UUID NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_by UUID,
    updated_by UUID,
    manufacturer CHARACTER VARYING(255) NOT NULL,
    model CHARACTER VARYING(255) NOT NULL,
    class CHARACTER VARYING(255) NOT NULL,
    category CHARACTER VARYING(255) NOT NULL,
    protocol CHARACTER VARYING(255) NOT NULL,
    authentication_type CHARACTER VARYING(255) NOT NULL,
    password CHARACTER VARYING(255),
    description CHARACTER VARYING(1000),
    status CHARACTER VARYING(50) NOT NULL DEFAULT 'ACTIVE'::character varying,
    status_reason CHARACTER VARYING(1000),
    serial CHARACTER VARYING(100),
    multiplier CHARACTER VARYING(50),
    security_policy CHARACTER VARYING(100),
    auth_mechanism CHARACTER VARYING(100),
    encryption_key CHARACTER VARYING(255),
    master_key CHARACTER VARYING(255),
    global_broadcast_encryption_key CHARACTER VARYING(255),
    destination_address CHARACTER VARYING(255),
    client_id CHARACTER VARYING(100),
    CONSTRAINT meter_integrations_pkey PRIMARY KEY (id),
    CONSTRAINT uk_meter_integration_manufacturer_model UNIQUE (manufacturer, model)
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
    existing_integration_id UUID;
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
        -- Get manufacturer name from UUID reference if present
        SELECT name INTO man_name FROM public.manufacturers WHERE id = r.meter_manufacturer;
        IF man_name IS NULL THEN
            man_name := 'GENERIC';
        END IF;

        -- Check if an integration with the same manufacturer and model already exists
        SELECT id INTO existing_integration_id
        FROM public.meter_integrations
        WHERE manufacturer = man_name AND model = COALESCE(r.meter_model, 'GENERIC')
        LIMIT 1;

        IF existing_integration_id IS NOT NULL THEN
            -- Use the existing integration ID
            UPDATE public.meters
            SET meter_integration_id = existing_integration_id
            WHERE id = r.meter_id;
        ELSE
            -- Generate a new UUID and insert
            new_integration_id := gen_random_uuid();

            INSERT INTO public.meter_integrations (
                id, version, created_at, updated_at, manufacturer, model, class, category, protocol, authentication_type, password, serial, status
            ) VALUES (
                new_integration_id,
                0,
                NOW(),
                NOW(),
                man_name,
                COALESCE(r.meter_model, 'GENERIC'),
                COALESCE(r.meter_class, 'GENERIC'),
                COALESCE(r.meter_category, 'GENERIC'),
                COALESCE(r.protocol, 'TCP'),
                COALESCE(r.authentication, 'NONE'),
                r.password,
                r.meter_number,
                'ACTIVE'
            );

            UPDATE public.meters
            SET meter_integration_id = new_integration_id
            WHERE id = r.meter_id;
        END IF;
    END LOOP;
END $$;

-- 5. Enforce NOT NULL constraints on newly populated foreign keys for data integrity
ALTER TABLE public.meters ALTER COLUMN organisation_id SET NOT NULL;
ALTER TABLE public.meters ALTER COLUMN meter_integration_id SET NOT NULL;
