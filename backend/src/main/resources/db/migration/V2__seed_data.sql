-- ============================================================
--  BIS AI ASSISTANT — Seed Data (V2)
-- ============================================================

-- Note: Admin account is initialized via secure environment bootstrap (AdminBootstrapRunner)
-- rather than hardcoded production seed data.

-- ============================================================
-- IS CATALOGUE — representative seed records
-- ============================================================
INSERT INTO is_catalogue (is_number, title, year, division, cert_scheme, is_mandatory, status) VALUES

-- Household / Consumer
('IS 2902', 'Hot Water Bottles — Specification', 2006, 'Chemical Division', 'SCHEME_I', TRUE,  'CURRENT'),
('IS 778',  'Copper Alloy Gate, Globe and Check Valves for Waterworks Purposes', 1984, 'Mechanical Engg', 'SCHEME_I', TRUE, 'CURRENT'),
('IS 302',  'Safety of Household and Similar Electrical Appliances', 2008, 'Electrotechnical', 'SCHEME_I', TRUE, 'CURRENT'),

-- Pressure / LPG
('IS 2825', 'Code for Unfired Pressure Vessels', 1969, 'Mechanical Engg', 'SCHEME_I', FALSE, 'CURRENT'),
('IS 18841','LPG Cylinders — Specification', 2023, 'Mechanical Engg', 'SCHEME_I', TRUE, 'CURRENT'),
('IS 3196', 'Welded Low Carbon Steel Cylinders Exceeding 5 L Water Capacity for LPG', 2019, 'Mechanical Engg', 'SCHEME_I', TRUE, 'CURRENT'),

-- Electrical / Electronics
('IS 14286','Solar Photovoltaic Energy Systems — Terms, Definitions and Symbols', 1995, 'Electrotechnical', 'SCHEME_I', FALSE, 'CURRENT'),
('IS 16221','LED Luminaires — Performance Requirements', 2015, 'Electrotechnical', 'CRS', TRUE, 'CURRENT'),
('IS 13252','IT Equipment — Safety Requirements', 2010, 'Electrotechnical', 'CRS', TRUE, 'CURRENT'),

-- Building / Steel
('IS 1786', 'High Strength Deformed Steel Bars and Wires for Concrete Reinforcement', 2008, 'Civil Engg', 'SCHEME_I', TRUE, 'CURRENT'),
('IS 2062', 'Hot Rolled Medium and High Tensile Structural Steel', 2011, 'Metallurgical Engg', 'SCHEME_I', FALSE, 'CURRENT'),
('IS 1489', 'Portland Pozzolana Cement', 1991, 'Civil Engg', 'SCHEME_I', TRUE, 'CURRENT'),

-- Plastics
('IS 6307', 'Rigid PVC Sheets — Specification', 2023, 'Polymer & Rubber', 'SCHEME_I', FALSE, 'CURRENT'),
('IS 4985', 'Unplasticized PVC Pipes for Potable Water Supplies', 2000, 'Polymer & Rubber', 'SCHEME_I', TRUE, 'CURRENT'),

-- Food / Packaging
('IS 2156', 'Tinplate — Specification', 1981, 'Metallurgical Engg', 'SCHEME_I', FALSE, 'CURRENT'),
('IS 9845', 'Method of Analysis for Plastic Materials in Contact with Foodstuffs', 1998, 'Polymer & Rubber', 'SCHEME_I', FALSE, 'CURRENT'),

-- Hallmarking
('IS 15820','Assaying and Hallmarking Centres — Requirements', 2009, 'Hallmarking', 'HALLMARKING', TRUE, 'CURRENT'),
('IS 1417', 'Grades of Gold and Gold Alloys — Jewellery/Artefact', 2016, 'Hallmarking', 'HALLMARKING', TRUE, 'CURRENT'),
('IS 2112', 'Silver — Methods of Assay', 1978, 'Hallmarking', 'HALLMARKING', FALSE, 'CURRENT'),

-- Medical
('IS 15883','Surgical Instruments — General Requirements', 2012, 'Medical Devices', 'SCHEME_I', TRUE, 'CURRENT'),
('IS 13940','Examination Gloves — Specification', 2018, 'Medical Devices', 'SCHEME_I', TRUE, 'CURRENT');
