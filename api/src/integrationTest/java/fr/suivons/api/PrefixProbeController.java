package fr.suivons.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Contrôleur de test : vérifie que les contrôleurs de l'API reçoivent le préfixe du contrat. */
@RestController
class PrefixProbeController {

    @GetMapping("/sonde-prefixe")
    String sonde() {
        return "ok";
    }
}
