package mg.ecoledimanche.presences.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RechercheTest {
    @Test
    fun normaliser_retireAccentsCasseEtEspaces() {
        assertEquals("eloise rakoto", Recherche.normaliser("  ÉLOÏSE   Rakoto "))
        assertEquals("andriamanjato", Recherche.normaliser("Andriamanjato"))
    }

    @Test
    fun correspond_ignoreCasseEtAccents_surNomEtPrenom() {
        assertTrue(Recherche.correspond(nom = "Rakoto", prenom = "Éloïse", requete = "eloise"))
        assertTrue(Recherche.correspond(nom = "Rakoto", prenom = "Éloïse", requete = "RAKOTO"))
        assertTrue(Recherche.correspond(nom = "Rakoto", prenom = "Éloïse", requete = "rako elo"))
        assertTrue(Recherche.correspond(nom = "Rakoto", prenom = "Éloïse", requete = "rakoto éloïse"))
        assertFalse(Recherche.correspond(nom = "Rakoto", prenom = "Éloïse", requete = "david"))
    }

    @Test
    fun requeteVide_correspondATout() {
        assertTrue(Recherche.correspond("Rakoto", "Sarah", ""))
        assertTrue(Recherche.correspond("Rakoto", "Sarah", "   "))
    }

    @Test
    fun comparateur_triSansDistinctionDeCasseNiDAccents() {
        val noms = listOf("Émilie" to "Z", "elodie" to "B", "Zoé" to "A", "Eloi" to "C", "émilie" to "A")
        val tries = noms.sortedWith(Recherche.comparateurNoms())
        assertEquals(listOf("elodie", "Eloi", "émilie", "Émilie", "Zoé"), tries.map { it.first })
        assertEquals("A", tries[2].second) // à prénom égal (accent et casse ignorés), tri par nom
    }
}
