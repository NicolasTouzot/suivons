# Image du front SSR (docker-compose, profil `stack`). Le bundle est construit au préalable : npm run build
# (dépendances incluses dans le bundle serveur : aucun node_modules nécessaire à l'exécution).
FROM node:24.21.0-alpine
WORKDIR /app
COPY dist/front ./dist/front
USER node
ENV PORT=4000
EXPOSE 4000
CMD ["node", "dist/front/server/server.mjs"]
