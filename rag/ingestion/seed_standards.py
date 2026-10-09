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
]


def seed():
    import uuid
    log.info("Ensuring Qdrant collection exists...")
    ensure_collection()
    for c in SAMPLE_STANDARDS:
        c.chunk_id = str(uuid.uuid5(uuid.NAMESPACE_DNS, c.chunk_id))
    log.info("Seeding representative BIS standard chunks...", count=len(SAMPLE_STANDARDS))
    upsert_chunks(SAMPLE_STANDARDS)
    log.info("Seeding completed successfully!")


if __name__ == "__main__":
    seed()
