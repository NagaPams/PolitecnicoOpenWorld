package ovh.gabrielhuav.pow.domain.models.streetfighter

import java.io.File
import com.google.gson.Gson
import org.junit.Test
import org.junit.Assert.*

class SfTeamRosterAssetAuditTest {
    private val assets = listOf(File("src/main/assets"), File("app/src/main/assets"))
        .first(File::isDirectory)

    @Test fun `las celdas de la hoja corresponden al orden actual del selector completo`() {
        val roster = Gson().fromJson(File(assets, "STREETFIGHTER/DATA/team_roster.json").readText(), Array<String>::class.java).toList()
        assertEquals(SfArcadeLadder.ALL_PARTICIPANTS.map { it.name }, roster)
        val texture = File(assets, "STREETFIGHTER/IMAGES/team_roster.webp")
        assertTrue(texture.isFile)
        assertTrue(texture.length() < 512000L)
        assertEquals("RIFF", texture.inputStream().use { String(it.readNBytes(4), Charsets.US_ASCII) })
    }
    @Test fun `los equipos posibles tienen arte y datos de combate existentes`() {
        SfArcadeLadder.ALL_PARTICIPANTS.forEach { fighter ->
            assertTrue(fighter.name, File(assets, fighter.spriteAsset).isFile)
            assertTrue(fighter.name, File(assets, fighter.jsonAsset).isFile)
        }
    }
}
