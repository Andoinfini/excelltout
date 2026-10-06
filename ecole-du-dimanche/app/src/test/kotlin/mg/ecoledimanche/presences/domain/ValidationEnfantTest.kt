package mg.ecoledimanche.presences.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidationEnfantTest {
    private val aujourdhui = LocalDate.parse("2026-10-06")

    private fun saisieValide() = SaisieEnfant(
        nom = "Rakoto",
        prenom = "Sarah",
        dateNaissance = LocalDate.parse("2017-03-15"),
        sexe = Sexe.FILLE,
    )

    private fun erreurs(saisie: SaisieEnfant): Map<ChampEnfant, ErreurChamp> =
        (ValidationEnfant.valider(saisie, aujourdhui) as ResultatValidation.Invalide).erreurs

    private fun valide(saisie: SaisieEnfant): DonneesEnfant =
        (ValidationEnfant.valider(saisie, aujourdhui) as ResultatValidation.Valide).donnees

    @Test
    fun saisieMinimale_estValide_etLesInformationsFacultativesSontNulles() {
        val d = valide(saisieValide())
        assertNull(d.adresse)
        assertNull(d.nomPere)
        assertNull(d.pereMembre) // non renseigné, jamais « Non » par défaut
        assertNull(d.nomMere)
        assertNull(d.mereMembre)
        assertNull(d.dateArrivee)
        assertEquals(0, d.nombreFreresSoeurs)
        assertEquals(0, d.nombreFreresSoeursMembres)
    }

    @Test
    fun lesQuatreChampsObligatoires_sontRequis() {
        val e = erreurs(SaisieEnfant())
        assertEquals(ErreurChamp.OBLIGATOIRE, e[ChampEnfant.NOM])
        assertEquals(ErreurChamp.OBLIGATOIRE, e[ChampEnfant.PRENOM])
        assertEquals(ErreurChamp.OBLIGATOIRE, e[ChampEnfant.DATE_NAISSANCE])
        assertEquals(ErreurChamp.OBLIGATOIRE, e[ChampEnfant.SEXE])
        assertEquals(4, e.size)
    }

    @Test
    fun espacesSuperfluxRetires_accentsConserves() {
        val d = valide(saisieValide().copy(nom = "  Ranaivoson  ", prenom = " Éloïse \t", adresse = "  Lot 12 Ambohijatovo  "))
        assertEquals("Ranaivoson", d.nom)
        assertEquals("Éloïse", d.prenom)
        assertEquals("Lot 12 Ambohijatovo", d.adresse)
    }

    @Test
    fun nomFaitSeulementDEspaces_estRefuse() {
        assertEquals(ErreurChamp.OBLIGATOIRE, erreurs(saisieValide().copy(nom = "   "))[ChampEnfant.NOM])
    }

    @Test
    fun dateDeNaissanceFuture_estRefusee_maisAujourdhuiEstAcceptee() {
        assertEquals(ErreurChamp.DATE_FUTURE, erreurs(saisieValide().copy(dateNaissance = LocalDate.parse("2026-10-07")))[ChampEnfant.DATE_NAISSANCE])
        assertEquals(aujourdhui, valide(saisieValide().copy(dateNaissance = aujourdhui)).dateNaissance)
    }

    @Test
    fun dateDArrivee_nePeutEtreNiFuture_niAvantLaNaissance() {
        val base = saisieValide()
        assertEquals(ErreurChamp.DATE_FUTURE, erreurs(base.copy(dateArrivee = LocalDate.parse("2026-12-25")))[ChampEnfant.DATE_ARRIVEE])
        assertEquals(ErreurChamp.ARRIVEE_AVANT_NAISSANCE, erreurs(base.copy(dateArrivee = LocalDate.parse("2017-03-14")))[ChampEnfant.DATE_ARRIVEE])
        assertEquals(LocalDate.parse("2017-03-15"), valide(base.copy(dateArrivee = LocalDate.parse("2017-03-15"))).dateArrivee)
        assertEquals(aujourdhui, valide(base.copy(dateArrivee = aujourdhui)).dateArrivee)
    }

    @Test
    fun nombresDeFreresEtSoeurs_entiersPositifsOuNuls() {
        val base = saisieValide()
        for (mauvais in listOf("-1", "1.5", "1,5", "abc", "1e2", "100", "٣")) {
            assertEquals("« $mauvais » doit être refusé", ErreurChamp.NOMBRE_INVALIDE, erreurs(base.copy(nombreFreresSoeurs = mauvais))[ChampEnfant.NB_FRERES_SOEURS])
        }
        assertEquals(0, valide(base.copy(nombreFreresSoeurs = "")).nombreFreresSoeurs) // vide = 0
        assertEquals(12, valide(base.copy(nombreFreresSoeurs = " 12 ", nombreFreresSoeursMembres = "0")).nombreFreresSoeurs)
    }

    @Test
    fun membresNePeutPasDepasserLeTotal() {
        val base = saisieValide()
        assertEquals(ErreurChamp.MEMBRES_SUPERIEUR_AU_TOTAL, erreurs(base.copy(nombreFreresSoeurs = "2", nombreFreresSoeursMembres = "3"))[ChampEnfant.NB_FRERES_SOEURS_MEMBRES])
        val d = valide(base.copy(nombreFreresSoeurs = "3", nombreFreresSoeursMembres = "3"))
        assertEquals(3, d.nombreFreresSoeursMembres)
        assertEquals(ErreurChamp.MEMBRES_SUPERIEUR_AU_TOTAL, erreurs(base.copy(nombreFreresSoeurs = "0", nombreFreresSoeursMembres = "1"))[ChampEnfant.NB_FRERES_SOEURS_MEMBRES])
    }

    @Test
    fun appartenanceDesParents_oui_non_nonRenseigne_sontConserves() {
        val d = valide(saisieValide().copy(nomPere = "Jean", pereMembre = true, nomMere = " Hanta ", mereMembre = false))
        assertEquals(true, d.pereMembre)
        assertEquals(false, d.mereMembre)
        assertEquals("Hanta", d.nomMere)
    }

    @Test
    fun deuxEnfantsDeMemeNomEtPrenom_sontAcceptes() {
        val a = valide(saisieValide())
        val b = valide(saisieValide())
        assertEquals(a, b) // la validation n'interdit pas les homonymes ; l'unicité est assurée par l'identifiant
    }

    @Test
    fun textesTropLongs_sontRefuses() {
        val long = "x".repeat(101)
        assertTrue(erreurs(saisieValide().copy(nom = long)).containsKey(ChampEnfant.NOM))
        assertEquals(ErreurChamp.TROP_LONG, erreurs(saisieValide().copy(adresse = "y".repeat(301)))[ChampEnfant.ADRESSE])
    }
}
