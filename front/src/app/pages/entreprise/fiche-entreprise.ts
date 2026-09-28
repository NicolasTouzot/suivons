import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, input, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';

import { EntreprisesService, EtatEntreprise, QualiteMontant } from '../../core/api/generated';
import { LIBELLES_CANAL, LIBELLES_NATURE } from '../../core/libelles';
import { MontantPipe } from '../../core/montant.pipe';

/** Taille des pages du tableau des flux. */
export const TAILLE_PAGE = 20;

/**
 * Fiche entreprise minimale (lot 1 bis) : identité, montant tracé, flux avec lien vers leur source (F2, F3).
 * La dénomination d'une entreprise non diffusible ou d'un entrepreneur individuel n'est jamais affichée.
 */
@Component({
  selector: 'app-fiche-entreprise',
  imports: [MontantPipe, RouterLink],
  templateUrl: './fiche-entreprise.html',
  styleUrl: './fiche-entreprise.scss',
})
export class FicheEntreprise {
  private readonly api = inject(EntreprisesService);

  /** SIREN de l'URL (`/entreprises/:siren`). */
  readonly siren = input.required<string>();
  readonly page = signal(1);

  readonly libellesCanal = LIBELLES_CANAL;
  readonly libellesNature = LIBELLES_NATURE;
  readonly etatActif = EtatEntreprise.Active;
  readonly aberrant = QualiteMontant.Aberrant;

  readonly synthese = rxResource({
    params: () => this.siren(),
    stream: ({ params }) => this.api.getEntreprise(params),
  });

  readonly flux = rxResource({
    params: () => ({ siren: this.siren(), page: this.page() }),
    stream: ({ params }) => this.api.getFluxEntreprise(params.siren, params.page, TAILLE_PAGE),
  });

  /** Nombre de pages du tableau des flux. */
  readonly pages = computed(() => Math.max(1, Math.ceil((this.flux.value()?.total ?? 0) / TAILLE_PAGE)));

  /** Message à afficher si la synthèse n'a pas pu être chargée. */
  readonly erreur = computed(() => {
    const erreur = this.synthese.error();
    if (!erreur) {
      return null;
    }
    const statut = erreur instanceof HttpErrorResponse ? erreur.status : (erreur.cause as HttpErrorResponse)?.status;
    if (statut === 404) {
      return 'Aucun flux tracé pour ce SIREN dans les sources observées.';
    }
    if (statut === 400) {
      return "Ce numéro n'est pas un SIREN valide.";
    }
    return 'Les données sont momentanément indisponibles. Réessayez dans quelques instants.';
  });

  allerA(page: number): void {
    this.page.set(Math.min(Math.max(1, page), this.pages()));
  }
}
