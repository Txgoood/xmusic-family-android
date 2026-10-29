package com.example

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Orijinal 18 Stitch ekranının APK kaynaklarında kaybolmasını önleyen statik kontrol. */
class StitchAssetIntegrityTest {
    private val folders = listOf(
        "ana_sayfa", "podcast_merkezi", "imdi_al_yor", "i_ndirme_y_neticisi",
        "m_zik_merkezi", "kitapl_m", "arama_ve_ke_fet", "aile_profili_se_imi",
        "sanat_detay", "alb_m_detay", "alma_listesi_y_netimi",
        "ocuk_modu_kids_safe", "ayarlar_ve_profil_y_netimi",
        "podcast_program_ve_b_l_m_detay", "oynatma_s_ras_queue",
        "uyku_zamanlay_c_s", "hata_ve_durum_ekranlar",
        "i_lk_kurulum_ve_profil_olu_turma"
    )

    @Test fun originalStitchPagesRemainPresent() {
        val assetRoot = listOf(File("src/main/assets/stitch"), File("app/src/main/assets/stitch"))
            .firstOrNull { it.isDirectory }
        assertTrue("Stitch asset root missing", assetRoot != null)
        folders.forEach { folder ->
            val page = File(assetRoot, "$folder/code.html")
            assertTrue("Missing original Stitch screen: $folder", page.isFile && page.length() > 100)
        }
    }
}
