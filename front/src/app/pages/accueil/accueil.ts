import { Component, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { Router } from '@angular/router';

import { lireSiren } from '../../core/libelles';

/** Accueil (SPEC.md §9.3) : recherche par SIREN ou SIRET ; la recherche par nom arrive au lot 3. */
@Component({
  selector: 'app-accueil',
  imports: [ReactiveFormsModule],
  templateUrl: './accueil.html',
  styleUrl: './accueil.scss',
})
export class Accueil {
  private readonly router = inject(Router);

  readonly formulaire = new FormGroup({ saisie: new FormControl('', { nonNullable: true }) });
  readonly erreur = signal(false);

  rechercher(): void {
    const siren = lireSiren(this.formulaire.controls.saisie.value);
    this.erreur.set(siren === null);
    if (siren !== null) {
      void this.router.navigate(['/entreprises', siren]);
    }
  }
}
