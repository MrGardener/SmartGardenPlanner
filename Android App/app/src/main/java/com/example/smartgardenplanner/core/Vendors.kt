package com.example.smartgardenplanner.core

/**
 * Seed vendor links (FR-023) and a preferred vendor (FR-024).
 *
 * Placeholder structure only, as agreed: the data model and UI slots exist so a real vendor integration
 * can be added later without restructuring, but no live vendor site is linked yet. [searchUrlTemplate]
 * is null for every entry, so [purchaseLink] always returns null and the UI shows the slot as
 * "not available yet".
 */
data class Vendor(val id: String, val displayName: String, val searchUrlTemplate: String?)

data class PurchaseLink(val vendor: Vendor, val url: String)

object VendorRegistry {

    val VENDORS: List<Vendor> = listOf(
        Vendor("vendor_a", "Seed vendor A (placeholder)", null),
        Vendor("vendor_b", "Seed vendor B (placeholder)", null),
        Vendor("vendor_c", "Local nursery (placeholder)", null)
    )

    fun byId(id: String?): Vendor? = VENDORS.firstOrNull { it.id == id }

    /**
     * The vendor to use: the preferred one when the user is allowed to choose (Pro, FR-024) and has chosen,
     * otherwise the first vendor.
     */
    fun effectiveVendor(preferredId: String?, canChoose: Boolean): Vendor =
        (if (canChoose) byId(preferredId) else null) ?: VENDORS.first()

    /** A purchase link for a variety, or null while vendors are placeholders. */
    fun purchaseLink(seed: SeedEntity, vendor: Vendor): PurchaseLink? {
        val template = vendor.searchUrlTemplate ?: return null
        val query = java.net.URLEncoder.encode(seed.commonName, "UTF-8")
        return PurchaseLink(vendor, template.replace("{query}", query))
    }
}
