import { Routes } from '@angular/router';

import { Accueil } from './pages/accueil/accueil';
import { FicheEntreprise } from './pages/entreprise/fiche-entreprise';

export const routes: Routes = [
  { path: '', component: Accueil, title: 'Suivre Notre Argent' },
  { path: 'entreprises/:siren', component: FicheEntreprise, title: 'Fiche entreprise · Suivre Notre Argent' },
  { path: '**', redirectTo: '' },
];
