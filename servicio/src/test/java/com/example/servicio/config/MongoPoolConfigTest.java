package com.example.servicio.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import com.mongodb.MongoClientSettings;
import com.mongodb.WriteConcern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Verifica el tuning del pool sin tocar Atlas: se invoca el customizer contra
 * un builder vacio y se leen los settings resultantes.
 *
 * Aqui no se pasa ninguna URI a proposito, porque el customizer no debe
 * tener opinion sobre la cadena de conexion: de eso se encarga Spring Boot.
 */
class MongoPoolConfigTest {

    private static final TimeUnit MS = TimeUnit.MILLISECONDS;

    private MongoClientSettings build() {
        MongoClientSettings.Builder builder = MongoClientSettings.builder();
        new MongoPoolConfig().mongoPoolCustomizer(
                20, 2,
                Duration.ofSeconds(60), Duration.ofMinutes(30),
                Duration.ofSeconds(5), Duration.ofSeconds(10),
                Duration.ofSeconds(45), Duration.ofSeconds(5))
                .customize(builder);
        return builder.build();
    }

    @Test
    @DisplayName("el pool queda acotado y con minimo tibio")
    void poolAcotadoConMinimoTibio() {
        var pool = build().getConnectionPoolSettings();

        assertThat(pool.getMaxSize()).isEqualTo(20);
        assertThat(pool.getMinSize()).isEqualTo(2);
        assertThat(pool.getMaxConnecting()).isEqualTo(20);
    }

    @Test
    @DisplayName("las conexiones ociosas vuelven antes de morir por idle de Atlas")
    void conexionesOciosasVuelvenAntesDeMorir() {
        var pool = build().getConnectionPoolSettings();

        assertThat(pool.getMaxConnectionIdleTime(MS)).isEqualTo(60_000L);
        assertThat(pool.getMaxConnectionLifeTime(MS)).isEqualTo(30 * 60_000L);
    }

    @Test
    @DisplayName("ante saturacion se falla rapido en vez de encolar")
    void fallaRapidoEnVezDeEncolar() {
        var client = build();

        assertThat(client.getConnectionPoolSettings().getMaxWaitTime(MS)).isEqualTo(5_000L);
        assertThat(client.getClusterSettings().getServerSelectionTimeout(MS)).isEqualTo(5_000L);
        assertThat(client.getSocketSettings().getConnectTimeout(MS)).isEqualTo(10_000L);
        assertThat(client.getSocketSettings().getReadTimeout(MS)).isEqualTo(45_000L);
    }

    @Test
    @DisplayName("los timeouts de socket no quedan sin limite")
    void losTimeoutsDeSocketNoQuedanSinLimite() {
        var socket = build().getSocketSettings();

        // 0 en el driver significa "sin limite", que es justo lo que se quiere
        // evitar: dejaria un cuelgue del cluster colgando este hilo.
        assertThat(socket.getReadTimeout(MS)).isPositive();
        assertThat(socket.getConnectTimeout(MS)).isPositive();
    }

    @Test
    @DisplayName("el customizer no decide la conexion, eso es de Spring Boot")
    void elCustomizerNoDecideLaConexion() {
        // Si este test empieza a fallar cuando se anade applyConnectionString
        // seria porque el customizer se esta adelantando a Spring Boot. El
        // write concern se queda en el reconocado por defecto del driver y no
        // en el "majority" que aporta la configuracion de la aplicacion.
        var client = build();

        assertThat(client.getWriteConcern()).isEqualTo(WriteConcern.ACKNOWLEDGED);
    }
}
