# Plan de implementación de tipos y adaptadores

Este plan reúne las decisiones de diseño para que otras bibliotecas puedan seleccionar adaptadores mediante metadatos de KProxyable en todas las plataformas. Cada paso debe incorporar sus pruebas antes de pasar al siguiente. La descripción de la API actual está en [TYPE_DESCRIPTORS.md](TYPE_DESCRIPTORS.md); los hechos comprobados sobre KSP están en [FUNCTION_TYPE_PROBE.md](FUNCTION_TYPE_PROBE.md).

Los bloques funcionales de los pasos 1 a 7 se han implementado para el alcance descrito en [Operaciones de tipos](TYPE_OPERATIONS.md). Las firmas y criterios siguientes describen el diseño implementado; ese documento detalla las APIs disponibles y las decisiones finales sobre dynamic y propiedades. La matriz de otras versiones y hosts del paso 8 continúa como verificación independiente.

## Estado actual

| Capacidad | Estado |
| --- | --- |
| Proyecciones estrella, in y out; parámetros identificados por declaración e índice | Implementado |
| Bounds múltiples, where y referencias recursivas finitas | Implementado |
| Nullability NOT_NULL, NULLABLE, UNMARKED y PLATFORM; dynamic separado | Implementado |
| Alias expandidos y clases valor con identidad propia | Implementado para los casos cubiertos por la suite |
| Tipos función con flags, firma calculada y fábrica canónica | Implementado |
| Receptores de métodos y callbacks suspendidos | Implementado |
| Sustitución pública, scope y nulabilidad efectiva | Implementado |
| Owner obligatorio y retirada de receiverType de propiedades | Implementado |
| Contrato universal de adaptadores o generación de SQL | Ejemplo integrado; sin contrato universal en el núcleo |

## Paso 1 Consolidar el contrato de tipos función

Mantener el modelo plano TypeDescriptor y sus invariantes de construcción. FunctionTypeFlags está presente en toda función Kotlin regular o suspendida, también con ambos booleanos false. El classifier es Function::class como marcador lógico. Sin flags, ese token puede describir el supertipo corriente Function<R>.

typeArguments almacena receptor opcional, parámetros ordinarios y retorno, sin Continuation implícito. functionType se calcula a partir de esa lista; una estrella conserva su posición y hace que la vista devuelva null. KFunctionN, KSuspendFunctionN y SAM permanecen como clasificadores ordinarios.

La fábrica pública es una comodidad que comparte las reglas de normalización con el generador:

```kotlin
val callback = TypeDescriptor.function(
    parameters = listOf(TypeProjection.Typed(TypeDescriptor(Int::class))),
    returnType = TypeProjection.Typed(TypeDescriptor(String::class)),
)
```

El constructor y copy exigen argumentos invariantes o estrellas cuando hay flags. La fábrica limpia in en entradas y out en resultados y rechaza proyecciones conflictivas. ExtensionFunctionType se representa con hasReceiver; ParameterName se elimina de entradas directas de cada nodo función, incluidos receptores. Se conservan anotaciones dentro de clasificadores ordinarios.

El predicado de familia compara declaraciones KSP cualificadas exactas, sin inspeccionar nombres de KClass en runtime. Los predicados funcionales de KSP también reconocen reflexivos en la versión comprobada.

Aceptación: equivalencia flecha, FunctionN, alias y fábrica; plain, receptor, suspend, suspend con receptor, nested, nullable, estrellas, proyecciones redundantes, clase propia Function1, reflexivos y aridad 23. Constructor/copy deben rechazar estados inválidos. La vista debe reflejar cambios de copy sin modificar el original. Mantener independientes las pruebas de símbolos KSP y las del contrato público.

## Paso 2 Alinear propiedades y diagnósticos

Hacer obligatorio PropertyDescriptor.owner y conservar como owner la interfaz para la que se crea el proxy. Retirar PropertyDescriptor.receiverType: el handler actual no transporta receptores de propiedades. Mantener un diagnóstico explícito al encontrar una propiedad de extensión.

Actualizar todos los constructores, ejemplos y generación en el mismo cambio. No retirar FunctionDescriptor.receiverType: sus valores sí llegan al handler como primer argumento.

Aceptación: lectura y escritura de propiedades normales y heredadas, owner del hijo, y fixture de compilación negativa para propiedades de extensión. Eliminar las expectativas antiguas de owner nullable.

## Paso 3 Introducir sustitución simultánea

API implementada:

```kotlin
fun TypeDescriptor.substitute(
    substitutions: Map<TypeParameterId, TypeDescriptor>,
): TypeDescriptor
```

Una pasada sustituye referencias originales sin volver a sustituir los resultados introducidos: con T → List<T>, el resultado de T es List<T>. No hace falta detectar ciclos del mapa ni resolverlo transitivamente. Recorrer argumentos Typed, conservar Star, anotaciones y flags. Aplicar la operación también a TypeParameterDescriptor.upperBounds y a los tipos de miembros. Los ids de las declaraciones no cambian.

Componer la marca externa explícitamente: T hereda la del reemplazo, T? aplica NULLABLE y T & Any aplica NOT_NULL. La composición con PLATFORM respeta la marca externa. DYNAMIC conserva su kind y nullability null. Hay fixtures de compilación y tests específicos en JVM y JS; véase TypeOperations.md.

Aceptación: sustitución dentro de List y tipos función; reemplazo de T por una función suspendida conservando flags; T? reemplazado por función produce callback nullable; reflexivos se sustituyen como clasificadores ordinarios; T → List<T> no recursa; los ids permanecen iguales. Para fun <R : T>, sustituir también el bound de R.

## Paso 4 Construir el scope y componer la herencia

API implementada: FunctionDescriptor.typeParameterScope(), con parámetros de interfaz y método indexados por TypeParameterId. Las colisiones por nombre no son colisiones por identidad. Ofrecer el equivalente necesario para propiedades. Un scope de declaraciones no equivale a conocer argumentos concretos de una instancia genérica.

Para Child : Mid<String> y Mid<X> : Parent<List<X>>, componer mapas hasta obtener T → List<String>. Conservar la identidad original del R declarado por Parent. Verificar si asMemberOf sustituye también sus bounds; completar explícitamente lo que KSP deje sin resolver.

Aceptación: herencia de dos niveles, parámetros con el mismo nombre en método e interfaz, fun <R : T> heredado y bounds recursivos como Comparable<T>. El handler no debe tener que reconstruir scopes a mano.

## Paso 5 Resolver nulabilidad efectiva

API implementada: EffectiveNullability con NOT_NULL, NULLABLE y UNKNOWN y una consulta que reciba el scope. Separar la forma declarada de la respuesta contextual.

| Caso | Resultado |
| --- | --- |
| Clasificador NOT_NULL o parámetro T & Any | NOT_NULL |
| Marca explícita NULLABLE, incluido T? | NULLABLE |
| T UNMARKED con algún bound que excluye null | NOT_NULL |
| T UNMARKED sin resolver y sin garantía en bounds | UNKNOWN |
| PLATFORM o DYNAMIC | UNKNOWN |

Bounds vacíos de un descriptor manual equivalen a Any? para esta consulta. Proteger la visita frente a ciclos defensivamente. UNKNOWN abarca dependencia de un argumento aún desconocido y ausencia de garantías estáticas; documentarlo. No reintroducir isNullable como booleano almacenado ni confundir un bound nullable con que todo uso concreto admita null.

Aceptación: T, T?, T & Any, T : Any, varios bounds con uno no nullable, bounds encadenados, vacíos y ciclos manuales; PLATFORM y DYNAMIC. Con sustitución concreta, T → String? debe conservar esa nulabilidad.

## Paso 6 Normalizar después de sustituir

API implementada: normalizeNullability(scope). Reducir la marca NOT_NULL de un parámetro a UNMARKED cuando sus bounds ya garantizan no nulidad. Conservar NOT_NULL para la intersección significativa de un parámetro con bound nullable.

El generador debe producir la forma canónica según estas reglas, sin obligar al consumidor a llamar al normalizador. Orden obligatorio para miembros heredados: resolver/componer sustituciones, sustituir usos y bounds, construir el scope efectivo y normalizar. T & Any con T → U y U : Any se reduce a U.

Aceptación: normalize(x) == x para descriptores generados, idempotencia en descriptores manuales, herencia de dos niveles y sustitución seguida de normalización. La igualdad sigue siendo estructural y utiliza la igualdad de las instancias de Annotation del backend; no promete equivalencia semántica general de tipos Kotlin.

## Paso 7 Validar una biblioteca de adaptadores sobre el núcleo

Una anotación con una clase identifica una opción; no permite instanciarla de forma portable. Elegir proveedores explícitos o generar llamadas Kotlin al constructor cuando la biblioteca conozca su contrato. La reflexión de JVM no debe ser necesaria.

Ejemplo orientativo de una biblioteca de almacenamiento construida sobre KProxyable:

```kotlin
interface ColumnAdapter<T> {
    fun decode(value: String): T
    fun encode(value: T): String
}

class UserIdAdapter : ColumnAdapter<UserId> {
    override fun decode(value: String) = UserId(value)
    override fun encode(value: UserId) = value.value
}

// Dependencias construidas explícitamente por la aplicación.
val adapter = UserIdAdapter()
```

El handler o código generado de esa biblioteca debe llamar realmente al adaptador en ambas direcciones. Forma ilustrativa del cuerpo de findById, con una API de base de datos hipotética:

```kotlin
fun findById(id: UserId): User? {
    val storedId = adapter.encode(id)
    val row = database.findRow(storedId) ?: return null
    return User(id = adapter.decode(row.id), name = row.name)
}
```

KProxyable genera el despacho al handler y los descriptores; la biblioteca de almacenamiento define la consulta, las columnas y la transformación. Una factory de adaptadores de retorno puede envolver un resultado, mientras un convertidor de columnas transforma valores: no imponer una interfaz única para ambos usos. Room, Retrofit y Ktorfit sirven como referencias de categorías; este ejemplo no pretende reproducir código generado por ellas.

Aceptación: ejemplo integrado sin reflexión, adaptador con dependencia inyectada, conversión de argumentos y resultados, null y entidad ausente, error de conversión y selección por tipo/anotaciones. Antes de ampliar el núcleo, demostrar qué información falta con ese ejemplo ejecutable.

## Paso 8 Migración y verificación continua

Recompilar runtime, procesador y consumidores con versiones alineadas. Documentar desaparición de isNullable, PARAMETER renombrado UNMARKED para nulabilidad, TypeKind.DYNAMIC, proyecciones, firma calculada de funciones, nuevo marcador lógico y exclusión de reflexivos. Incorporar los cambios de propiedades cuando se implementen.

Cada paso lleva sus tests de unidad, integración con proxies y, cuando dependa de KSP o reglas Kotlin, fixtures positivos y negativos contra el compilador. No añadir pruebas que anuncien como disponible una API pendiente. El diagnóstico de propiedades tiene una prueba negativa reproducible en scratch/test_extension_property_diagnostic.ps1.

Ejecutar todas las pruebas disponibles: JVM, JS y Wasm en Node y Edge, Native mingwX64 y Native linuxX64 mediante WSL. Apple requiere macOS y Linux ARM64 otro entorno compatible. Kotlin 2.0.21 y KSP 2.0.21-1.0.28 son la combinación comprobada; el mínimo soportado y la matriz de versiones se verifican aparte.

Los comandos reproducibles están en [TYPE_DESCRIPTORS.md](TYPE_DESCRIPTORS.md#verificación-del-contrato-de-funciones). Registrar recuentos desde XML, distinguir NO-SOURCE de tests ejecutados y no sumar informes históricos. Los resultados de esta revisión se detallan a continuación.


## Resultado de la verificación completa

Con Kotlin 2.0.21 y KSP 2.0.21-1.0.28 pasan **280 ejecuciones**, sin fallos, errores ni tests omitidos: 241 en Windows y 39 en Native Linux x64. Se forzaron las 140 tareas de Windows y las 28 de Linux mediante --rerun-tasks. Los módulos sin fuentes de test no cuentan como casos ejecutados.

| Entorno | Casos |
| --- | ---: |
| jvmTest | 40 |
| jsNodeTest | 42 |
| wasmJsNodeTest | 39 |
| mingwX64Test | 39 |
| jsBrowserTest | 41 |
| wasmJsBrowserTest | 38 |
| test | 2 |
| linuxX64Test | 39 |

Además pasa la prueba negativa de compilación de propiedades de extensión, ejecutada mediante scratch/test_extension_property_diagnostic.ps1. El fixture temporal se retira al terminar. La comprobación exige el diagnóstico específico, no cualquier fallo del compilador.

Las pruebas incluyen propiedades heredadas de lectura/escritura y su owner, composición de dos niveles de herencia y bounds de métodos, sustitución simultánea, scopes, ciclos defensivos, normalización idempotente, funciones y reflexivos, dynamic en JS, plataforma Java en JVM y conversiones reales mediante adaptadores inyectados. Los tests anteriores de metadatos y enlaces entre módulos siguen pasando.

Logs: build/all-windows-type-operations.log, build/linux-type-operations.log y build/extension-property-negative.log. Los XML están en build/test-results de cada módulo. Apple, Linux ARM64 y otras combinaciones de Kotlin/KSP no se han ejecutado en esta matriz.
