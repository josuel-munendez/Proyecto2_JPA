package com.example.servicio.entity;

/**
 * Grupos de validacion de Bean Validation (Jakarta Validation).
 *
 * <h2>Por que hacen falta</h2>
 * La entidad {@link Producto} mapea {@code products_product}, una tabla que
 * comparte con Django y que tiene filas anteriores a la migracion
 * {@code products.0007_add_referencia_stock}. Esas 34 filas heredadas tienen
 * {@code referencia = ''} (cadena vacia, no NULL).
 *
 * <p>Sin grupos, {@code @Pattern} en {@code referencia} revienta en CUALQUIER
 * escritura sobre esas filas —incluido el borrado logico, que solo cambia
 * {@code is_active}/{@code is_approved}— y el {@code DELETE} del catalogo
 * responde 500 en vez de 204. Se comprobó empiricamente antes de escribir esto:
 * portar las 5 anotaciones sin grupos devolvia
 * {@code ConstraintViolationException: propertyPath=referencia} en 34 de 36
 * productos.
 *
 * <h2>Como se reparten</h2>
 * <ul>
 *   <li><b>Default</b> (implícito): las validaciones que los datos heredados
 *       SI cumplen. Hibernate las aplica solo, en cada INSERT y en cada
 *       UPDATE, sin que nadie llame a un validator.</li>
 *   <li><b>AlCrear</b>: las que solo tienen sentido al dar de alta. Se
 *       validan explicitamente en
 *       {@code ProductoServiceImpl.crearProducto} antes del {@code save}.</li>
 * </ul>
 *
 * <p>Es el mismo criterio que usa Spring Security con
 * {@code groups = OnCreate.class}, y la razon de fondo es la misma: no se puede
 * exigir un invariante a datos que ya existen y no lo cumplen.
 */
public interface ProductoGrupos {

    /**
     * Validaciones que se exigen unicamente al crear un producto.
     *
     * <p>Hoy la unica es el formato de {@code referencia}, porque es el campo
     * que las filas heredadas de Django tienen vacio.</p>
     */
    interface AlCrear {
    }
}
