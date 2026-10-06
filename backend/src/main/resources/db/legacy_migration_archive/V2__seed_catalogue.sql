INSERT INTO is_catalogue (is_number, title, year, division, cert_scheme, mandatory, status) VALUES
('IS 2902', 'Pressure Cookers — Specification', 2023, 'Mechanical Engineering', 'SCHEME_I', false, 'CURRENT'),
('IS 2347', 'Domestic Gas Stoves for Use with Piped Natural Gas', 2017, 'Mechanical Engineering', 'SCHEME_I', false, 'CURRENT'),
('IS 15644', 'LPG Cylinders — Valves — Specification', 2018, 'Mechanical Engineering', 'SCHEME_I', false, 'CURRENT')
ON CONFLICT (is_number) DO NOTHING;
