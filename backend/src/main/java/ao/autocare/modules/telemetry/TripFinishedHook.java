package ao.autocare.modules.telemetry;

import ao.autocare.domain.Trip;

/**
 * Avisado quando uma viagem fecha.
 *
 * <p>Existe para o modulo de frota poder analisar a conducao sem que a
 * telemetria passe a depender dele. A seta de dependencia aponta para esta
 * interface dos dois lados, e nao de um modulo para o outro -- de outra forma
 * fechar uma viagem e pontuar um motorista ficavam presos um ao outro.
 */
public interface TripFinishedHook {

    void onTripFinished(Trip trip);
}
