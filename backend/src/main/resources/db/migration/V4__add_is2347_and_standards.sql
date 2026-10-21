-- ============================================================
--  BIS AI ASSISTANT — Migration V4
--  Add IS 2347 (Domestic Pressure Cookers) and other core standards
-- ============================================================

INSERT INTO is_catalogue (is_number, title, year, division, cert_scheme, is_mandatory, status)
VALUES
    ('IS 2347', 'Domestic Pressure Cookers — Specification', 2017, 'Mechanical Engg', 'SCHEME_I', TRUE, 'CURRENT'),
    ('IS 302-2-3', 'Safety of Household and Similar Electrical Appliances: Electric Irons', 2007, 'Electrotechnical', 'SCHEME_I', TRUE, 'CURRENT'),
    ('IS 14543', 'Packaged Drinking Water (Other Than Packaged Natural Mineral Water)', 2004, 'Food and Agriculture', 'SCHEME_I', TRUE, 'CURRENT'),
    ('IS 1293', 'Plugs and Socket-Outlets of Rated Voltage up to and Including 250 V', 2019, 'Electrotechnical', 'SCHEME_I', TRUE, 'CURRENT'),
    ('IS 269', 'Ordinary Portland Cement — Specification', 2015, 'Civil Engg', 'SCHEME_I', TRUE, 'CURRENT'),
    ('IS 4984', 'Polyethylene Pipes for Water Supply — Specification', 2016, 'Polymer & Rubber', 'SCHEME_I', TRUE, 'CURRENT'),
    ('IS 16102', 'Self-Ballasted LED Lamps for General Lighting Services', 2014, 'Electrotechnical', 'CRS', TRUE, 'CURRENT')
ON CONFLICT (is_number) DO UPDATE SET
    title = EXCLUDED.title,
    year = EXCLUDED.year,
    division = EXCLUDED.division,
    cert_scheme = EXCLUDED.cert_scheme,
    is_mandatory = EXCLUDED.is_mandatory,
    status = EXCLUDED.status;
