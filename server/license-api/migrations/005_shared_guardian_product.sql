-- Add one platform-neutral product for Windows and Android clients.
-- Existing Android product rows and licenses remain valid for backward compatibility.
INSERT INTO products(code, name, entitlement)
VALUES ('guardian-pro', 'Guardian Pro', 'pro')
ON CONFLICT (code) DO NOTHING;
