## Summary

<!-- What does this PR do? One or two sentences. -->

## Type of change

- [ ] Bug fix
- [ ] New feature
- [ ] Refactor / code cleanup
- [ ] Tooling / CI / workflow
- [ ] Documentation

## Changes

<!-- Bullet-point list of what changed and why. -->

## How to test

<!-- Steps to verify the change works as expected. -->
1.
2.

## Checklist

- [ ] Backend: `./mvnw test` passes locally
- [ ] Backend: `./mvnw spotless:check` passes (or `spotless:apply` was run first)
- [ ] Frontend: `npm test -- --watch=false --browsers=ChromeHeadless` passes locally
- [ ] Frontend: `npm run lint` passes
- [ ] Frontend: `npm run format:check` passes (or `npm run format` was run first)
- [ ] New migrations follow the `V{n}__description.sql` naming convention
- [ ] No secrets or `.env` files committed
