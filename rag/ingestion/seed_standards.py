"""
Seed representative BIS document chunks into Qdrant vector database
aligned with the Project Architecture & Test Cases guide.
"""

import sys
from pathlib import Path

# Ensure the rag/ root is on sys.path so this script works when executed
# directly from the ingestion/ subfolder (e.g. `python seed_standards.py`).
_rag_root = Path(__file__).resolve().parent.parent
if str(_rag_root) not in sys.path:
    sys.path.insert(0, str(_rag_root))

from ingestion.ingestor import DocumentChunk, upsert_chunks, ensure_collection  # noqa: E402
import structlog

log = structlog.get_logger()

SAMPLE_STANDARDS = [
    # ── Test Case 5.2 & Household Electric Irons ──
    DocumentChunk(
        chunk_id="is-302-2-3-scope",
        content=(
            "IS 302-2-3:2007 (Safety of Household and Similar Electrical Appliances — "
            "Part 2: Particular Requirements — Section 3: Electric Irons). "
            "This standard deals with the safety of electric dry irons and steam irons for household and similar purposes, "
            "their rated voltage being not more than 250 V for single-phase a.c. "
            "Applies to domestic electric irons, travel irons, and cord-connected or cordless irons. "
            "Performance requirements are covered under IS 366 (Electric Irons — Performance Requirements). "
            "Domestic electric irons are covered under mandatory Quality Control Order (QCO) issued by the Ministry of Heavy Industries "
            "under the Bureau of Indian Standards Act, 2016. "
            "Under Section 17 of the BIS Act 2016, manufacturing, importing, stocking, or selling non-compliant goods without the ISI Mark "
            "is punishable with imprisonment for a term up to two years or with fine up to ten times the value of goods."
        ),
        is_number="IS 302-2-3:2007",
        clause_ref="Clause 1.1 & QCO Notification",
        section_title="Scope, Performance & Mandatory Certification of Electric Irons",
        doc_type="STANDARD",
        source_url="https://services.bis.gov.in",
        page_number=1,
    ),

    # ── Test Case 1.1: Packaged Drinking Water ──
    DocumentChunk(
        chunk_id="is-14543-microbio",
        content=(
            "IS 14543:2004 Packaged Drinking Water (Other than Packaged Natural Mineral Water) — Specification. "
            "Clause 5.2 Microbiological Requirements: Packaged drinking water shall be free from microbiological contamination. "
            "Limits: Escherichia coli shall be absent in 250 ml. Coliform bacteria shall be absent in 250 ml. "
            "Faecal streptococci and Staphylococcus aureus shall be absent in 250 ml. "
            "Pseudomonas aeruginosa shall be absent in 250 ml. Sulphite reducing anaerobes absent in 50 ml. "
            "Yeast and mould count: Absent in 250 ml. Total viable colony count at 37°C/24h shall not exceed 100 CFU/ml. "
            "Physical and chemical limits: pH range shall be 6.5 to 8.5. Total Dissolved Solids (TDS) maximum 500 mg/l. "
            "Turbidity maximum 2 NTU. Contrasted with IS 13428 for Packaged Natural Mineral Water which originates from natural springs."
        ),
        is_number="IS 14543:2004",
        clause_ref="Clause 5.2",
        section_title="Microbiological & Chemical Specifications for Packaged Drinking Water",
        doc_type="STANDARD",
        source_url="https://services.bis.gov.in",
        page_number=4,
    ),

    # ── Test Case 1.2: Household Plugs and Socket-Outlets ──
    DocumentChunk(
        chunk_id="is-1293-safety",
        content=(
            "IS 1293:2019 Plugs and Socket-Outlets of Rated Voltage up to and Including 250 V and Rated Current up to and Including 16 A. "
            "Covers configurations for 2.5A, 6A, 10A, and 16A plugs and socket-outlets for domestic and similar use. "
            "Key safety requirements include mandatory safety shutters on socket-outlets to prevent accidental contact, "
            "specified pin pitch and dimensional tolerances, and insulation sleeves on live pins for shock protection. "
            "IS 1293 is under mandatory Quality Control Order (QCO) issued by DPIIT; no plug or socket may be sold in India without the ISI mark."
        ),
        is_number="IS 1293:2019",
        clause_ref="Clause 3 & Safety Requirements",
        section_title="Plugs and Socket-Outlets Safety & QCO Mandate",
        doc_type="STANDARD",
        source_url="https://services.bis.gov.in",
        page_number=2,
    ),

    # ── Test Case 1.3: Cement Specifications ──
    DocumentChunk(
        chunk_id="is-269-cement",
        content=(
            "IS 269:2015 Ordinary Portland Cement — Specification (Sixth Revision). "
            "Integrates 33 Grade, 43 Grade, and 53 Grade Ordinary Portland Cement (superseding old IS 12269 for 53 grade). "
            "Compressive strength requirement for 53 Grade OPC: Minimum 28-day compressive strength shall be 53 MPa (N/mm²). "
            "7-day compressive strength minimum 37 MPa; 3-day minimum 27 MPa. "
            "Physical limits: Initial setting time not less than 30 minutes; final setting time not more than 600 minutes. "
            "Fineness by specific surface area: Not less than 225 m²/kg."
        ),
        is_number="IS 269:2015",
        clause_ref="Clause 6.1 & Table 2",
        section_title="Physical and Chemical Requirements of 53 Grade OPC Cement",
        doc_type="STANDARD",
        source_url="https://services.bis.gov.in",
        page_number=3,
    ),

    # ── Test Case 2.1: Scheme-I ISI Mark Certification Process ──
    DocumentChunk(
        chunk_id="scheme-1-licensing-steps",
        content=(
            "BIS Product Certification Scheme-I (ISI Mark) — Grant of Licence Guidelines. "
            "Step-by-step procedure for domestic manufacturers (e.g., ceiling fans under IS 374, pressure cookers under IS 2347): "
            "Step 1: Set up in-house laboratory and testing facilities as per the prescribed Scheme of Inspection and Testing (SIT). "
            "Step 2: Submit online application on the Manakonline portal (manakonline.in) along with requisite fees. "
            "Step 3: Verification inspection by a BIS technical auditor at the manufacturing premises to examine machinery, quality control, and testing competency. "
            "Step 4: Drawing of verification samples by BIS officer for independent testing at a BIS-recognized laboratory. "
            "Step 5: Grant of Licence (CML number) upon satisfactory test results and payment of marking fee, authorizing affixing of the ISI Mark. "
            "Routes: Normal Route (average 60–90 days) and Simplified Route for select products (approx 30 days based on prior lab test reports)."
        ),
        is_number="Scheme-I Guidelines",
        clause_ref="Section 4 & 5",
        section_title="Step-by-Step Procedure for Grant of BIS Licence under Scheme-I",
        doc_type="SCHEME_GUIDELINE",
        source_url="https://www.manakonline.in",
        page_number=1,
    ),

    # ── Test Case 2.2: Foreign Manufacturers Certification Scheme (FMCS) ──
    DocumentChunk(
        chunk_id="fmcs-guidelines",
        content=(
            "Foreign Manufacturers Certification Scheme (FMCS) — BIS Scheme-I for Overseas Manufacturers. "
            "Enables foreign manufacturers located outside India to obtain a BIS licence and use the ISI Mark on products exported to India. "
            "Mandatory Requirement: The foreign manufacturer must nominate an Authorized Indian Representative (AIR) resident in India, "
            "who represents the manufacturer and accepts legal liability for compliance with the BIS Act, rules, and regulations. "
            "Procedure: Submission of Form-I application with audit fee in USD. Factory audit conducted by BIS officers at the overseas manufacturing site. "
            "Drawal of samples during audit for testing in India. Grant of licence upon confirmation of conformity and payment of marking fee."
        ),
        is_number="FMCS Scheme-I",
        clause_ref="Guidelines for Overseas Manufacturers",
        section_title="Requirements and Procedure for Foreign Manufacturers Certification Scheme (FMCS)",
        doc_type="SCHEME_GUIDELINE",
        source_url="https://www.bis.gov.in/fmcs",
        page_number=1,
    ),

    # ── Test Case 3.1: Gold Hallmarking & HUID ──
    DocumentChunk(
        chunk_id="is-1417-hallmarking",
        content=(
            "IS 1417:2016 Gold and Gold Alloys, Jewellery/Artefacts — Fineness and Marking. "
            "Mandatory hallmarking applies to gold jewellery in specified caratages: 14K (585), 18K (750), 20K (833), 22K (916), 23K (958), and 24K (995/999). "
            "Components of the BIS Hallmark on Gold Jewellery: "
            "1. BIS Standard Mark (triangle logo). "
            "2. Purity in Karat and Fineness (e.g., 22K916 for 22 Karat 91.6% pure gold). "
            "3. 6-digit alphanumeric Hallmarking Unique Identification (HUID) code unique to each piece of jewellery. "
            "Consumers can verify authenticity of the HUID code and details of assaying centre and jeweller using the 'Verify HUID' feature on the BIS CARE App."
        ),
        is_number="IS 1417:2016",
        clause_ref="Clause 4 & Hallmarking Order",
        section_title="Gold Hallmarking Signs, Caratage and HUID Verification",
        doc_type="STANDARD",
        source_url="https://services.bis.gov.in",
        page_number=2,
    ),

    # ── Test Case 5.1: HDPE Pipes ──
    DocumentChunk(
        chunk_id="is-4984-hdpe",
        content=(
            "IS 4984:2016 Polyethylene Pipes for Water Supply — Specification (Fifth Revision). "
            "Specifies requirements for high-density polyethylene (HDPE) pipes for water supply and municipal systems. "
            "Material Grades covered: PE 63, PE 80, and PE 100. "
            "Pressure ratings (PN): PN 2.5, PN 4, PN 6, PN 10, PN 12.5, and PN 16. "
            "Quality and compliance tests include internal hydrostatic pressure test at 27°C and 80°C, "
            "Oxidation Induction Time (OIT) minimum 20 minutes at 200°C, and Melt Flow Rate (MFR) compatibility."
        ),
        is_number="IS 4984:2016",
        clause_ref="Clause 5, 6 & Table 3",
        section_title="HDPE Pipe Grades, Pressure Ratings and Hydrostatic Strength",
        doc_type="STANDARD",
        source_url="https://services.bis.gov.in",
        page_number=3,
    ),

    # ── IS 2347: Domestic Pressure Cookers — Scope, Materials & Construction ──
    DocumentChunk(
        chunk_id="is-2347-scope-materials",
        content=(
            "IS 2347:2017 Domestic Pressure Cookers — Specification (Fifth Revision). "
            "Clause 1 & 4 Scope and Material Requirements: Covers domestic pressure cookers "
            "operating at a normal working pressure of 1.0 kgf/cm² (approximately 100 kPa or 1 bar), with nominal capacities up to 15 litres. "
            "All materials in contact with food shall be non-toxic, non-contaminating, and corrosion-resistant. "
            "Body and lid materials shall be manufactured from: "
            "1) Wrought aluminium or aluminium alloy sheet conforming to IS 21 or IS 737, with minimum body base thickness of 3.25 mm to prevent warping, localized overheating, and pit corrosion; "
            "2) Austenitic stainless steel conforming to Grade 04Cr18Ni10 (AISI 304) of IS 6911; or "
            "3) Composite / tri-ply construction having an aluminium core sandwiched between stainless steel layers. "
            "Sealing gaskets must be made of food-grade synthetic or natural rubber conforming to IS 7466, resistant to boiling water, steam, vegetable oils, and animal fats. "
            "Handles shall be manufactured from heat-insulating, flame-resistant material (such as phenolic moulding powder conforming to IS 1300) and rigidly fastened. "
            "Domestic pressure cookers are under mandatory Quality Control Order (QCO) issued by DPIIT; "
            "no pressure cooker may be manufactured, imported, or sold in India without the BIS ISI Mark under Scheme-I certification."
        ),
        is_number="IS 2347:2017",
        clause_ref="Clause 1, 4 & QCO",
        section_title="Domestic Pressure Cookers — Scope, Material Grades, Gasket & QCO Mandate",
        doc_type="STANDARD",
        source_url="https://services.bis.gov.in",
        page_number=1,
    ),

    # ── IS 2347: Domestic Pressure Cookers — Safety Devices & GRS ──
    DocumentChunk(
        chunk_id="is-2347-safety-devices",
        content=(
            "IS 2347:2017 Domestic Pressure Cookers — Specification. "
            "Clause 5 & 6 Safety Devices, Pressure Regulation and Safety Interlock Mechanism: "
            "Domestic pressure cookers must incorporate a three-tier safety system: "
            "1) Normal Pressure Regulating Device (Vent Weight / Weight Valve): Operates at the nominal cooking working pressure of 1.0 kgf/cm² (100 kPa), "
            "releasing excess steam continuously and smoothly. "
            "2) Secondary Safety Device (Safety Valve / Fusible Plug / Spring-Loaded Relief Valve): Operates automatically if the vent pipe or weight valve becomes clogged or obstructed. "
            "The safety valve shall operate and release pressure within the safety threshold range between 1.3 kgf/cm² and 2.0 kgf/cm² (130 kPa to 200 kPa) before dangerous pressure can accumulate. "
            "3) Gasket Release System (GRS): A tertiary fail-safe mechanism where an aperture in the lid rim allows the gasket to deform and release excessive internal pressure downwards in case both vent tube and safety valve fail. "
            "4) Lid Safety Interlocking Mechanism: Cooker design shall prevent accidental removal or forced opening of the lid while internal pressure exceeds 4 kPa (0.04 kgf/cm²), protecting users from explosive opening and steam burns."
        ),
        is_number="IS 2347:2017",
        clause_ref="Clause 5 & 6",
        section_title="Three-Tier Safety Devices: Weight Valve, Safety Valve, GRS and Lid Interlock",
        doc_type="STANDARD",
        source_url="https://services.bis.gov.in",
        page_number=3,
    ),

    # ── IS 2347: Domestic Pressure Cookers — Testing, Performance & Marking ──
    DocumentChunk(
        chunk_id="is-2347-testing-marking",
        content=(
            "IS 2347:2017 Domestic Pressure Cookers — Specification. "
            "Clause 7, 8 & 9 Testing, Performance Requirements and Mandatory Markings: "
            "1) Hydrostatic Proof Pressure Test: The cooker body and lid assembly shall withstand a hydrostatic proof pressure of not less than 2.0 kgf/cm² (200 kPa) "
            "— twice the normal operating pressure — for at least 2 minutes without leakage, structural rupture, or permanent distortion. "
            "2) Bursting Pressure Test: The cooker shall withstand a minimum hydraulic bursting pressure of 3.0 kgf/cm² (300 kPa) without rupture. "
            "3) Operating Pressure Test: Tested on boiling water; the weight valve must maintain operating pressure within ±10% of 1.0 kgf/cm². "
            "4) Handle Temperature Rise and Strength: Handle surface temperature shall not rise more than 55°C above ambient during prolonged operation. Handles must pass a cantilever load test without loosening, cracking, or damage. "
            "5) Mandatory Markings: Each pressure cooker shall be clearly marked with manufacturer trade-mark, model number, nominal capacity in litres, "
            "rated operating pressure (1.0 kgf/cm²), warning 'Read instructions before use — Do not open under pressure', and the BIS Standard Mark (ISI mark) with CML licence number."
        ),
        is_number="IS 2347:2017",
        clause_ref="Clause 7, 8 & 9",
        section_title="Proof Pressure, Bursting Strength, Operating Tests and ISI Markings",
        doc_type="STANDARD",
        source_url="https://services.bis.gov.in",
        page_number=5,
    ),

    # ── IS 16102 (Part 1): Self-Ballasted LED Lamps — Safety Requirements ──
    DocumentChunk(
        chunk_id="is-16102-1-safety",
        content=(
            "IS 16102 (Part 1):2014 Self-Ballasted LED Lamps for General Lighting Services — Part 1: Safety Requirements. "
            "Covers self-ballasted LED lamps with supply voltages up to 250 V a.c. 50 Hz and rated wattages up to 60 W for domestic and similar general lighting. "
            "Key safety requirements: "
            "1) Marking: Rated wattage, rated voltage, frequency, lamp cap designation (e.g., B22d or E27), luminous flux, and BIS CRS Registration Mark (R-number). "
            "2) Insulation Resistance and Electric Strength: Minimum insulation resistance of 4 MΩ between live parts and accessible body parts; withstands 4000 V a.c. dielectric test. "
            "3) Mechanical Strength: Lamp cap torque test (B22d caps withstand 3 Nm, E27 caps withstand 3 Nm) without loosening, cracking, or turning. "
            "4) Resistance to Heat and Fire: Non-metallic parts insulating live parts must pass glow-wire test at 650°C and ball pressure test at 125°C. "
            "5) Mandatory Certification: Self-ballasted LED lamps are covered under mandatory Compulsory Registration Scheme (CRS) notified by MeitY; "
            "manufacturers must register on the BIS portal (crsbis.in) based on testing at a BIS-recognised laboratory."
        ),
        is_number="IS 16102 (Part 1):2014",
        clause_ref="Clause 4, 6, 8 & CRS Mandate",
        section_title="Self-Ballasted LED Lamps — Safety Requirements, Electric Strength and CRS Mandate",
        doc_type="STANDARD",
        source_url="https://services.bis.gov.in",
        page_number=1,
    ),

    # ── IS 16102 (Part 2): Self-Ballasted LED Lamps — Performance Requirements ──
    DocumentChunk(
        chunk_id="is-16102-2-performance",
        content=(
            "IS 16102 (Part 2):2012 Self-Ballasted LED Lamps for General Lighting Services — Part 2: Performance Requirements. "
            "Specifies performance and quality criteria for LED bulbs: "
            "1) Lamp Wattage: Measured lamp wattage shall not exceed 110% of rated wattage. "
            "2) Luminous Flux and Efficacy: Initial lumen output shall not be less than 90% of rated luminous flux. "
            "3) Correlated Colour Temperature (CCT) and CRI: Standard CCT values: 2700 K (Warm White), 4000 K (Neutral White), and 6500 K (Cool Daylight). "
            "Colour Rendering Index (CRI, Ra) shall not be less than 80 for indoor domestic applications. "
            "4) Power Factor: Power factor shall be at least 0.90 for lamps above 5 W rating. "
            "5) Life and Lumen Maintenance: Rated life minimum 15,000 to 25,000 hours with minimum 70% lumen maintenance (L70) at end of life."
        ),
        is_number="IS 16102 (Part 2):2012",
        clause_ref="Clause 6, 7 & 8",
        section_title="Self-Ballasted LED Lamps — Luminous Flux, CCT, CRI, Power Factor and Life",
        doc_type="STANDARD",
        source_url="https://services.bis.gov.in",
        page_number=2,
    ),
]


def seed(recreate: bool = False):
    import uuid
    from ingestion.ingestor import qdrant_sync
    from config import settings
    if recreate:
        log.info("Deleting existing Qdrant collection for clean reseed...", name=settings.qdrant_collection)
        try:
            qdrant_sync.delete_collection(settings.qdrant_collection)
        except Exception as e:
            log.warning("Could not delete collection", error=str(e))
    log.info("Ensuring Qdrant collection exists...")
    ensure_collection()
    for c in SAMPLE_STANDARDS:
        c.chunk_id = str(uuid.uuid5(uuid.NAMESPACE_DNS, c.chunk_id))
    log.info("Seeding representative BIS standard chunks...", count=len(SAMPLE_STANDARDS))
    upsert_chunks(SAMPLE_STANDARDS)
    log.info("Seeding completed successfully!")


if __name__ == "__main__":
    recreate_flag = "--recreate" in sys.argv
    seed(recreate=recreate_flag)
