# Descriptores de tipos de KProxyable

El orden de implementación, las APIs y sus criterios de aceptación están en el [plan de implementación](IMPLEMENTATION_PLAN.md).

Los handlers necesitan conocer los tipos declarados, sus proyecciones, anotaciones y restricciones para seleccionar adaptadores sin reflexión. Este diseño conserva esa información mediante expresiones Kotlin generadas por KSP. No interpreta SQL, no construye entidades y no define un contrato universal de adaptadores.

## Representación de los tipos

`TypeDescriptor.kind` distingue tres formas: `CLASSIFIER` para clases, interfaces, objetos y enums; `PARAMETER` para referencias a parámetros genéricos; y `DYNAMIC` para `dynamic` de Kotlin/JS. No se distingue clase de interfaz dentro de TypeKind: ambas utilizan un clasificador.

El constructor valida cada forma. `CLASSIFIER` exige `classifier` sin `typeParameter`; `PARAMETER` exige `typeParameter` sin `classifier`, argumentos ni firma de función. `DYNAMIC` no tiene clasificador, parámetro, argumentos, firma de función ni nulabilidad independiente. Las tres formas pueden conservar anotaciones sobre el tipo. Los clasificadores no reconocidos producen un error de procesamiento: nunca se sustituyen silenciosamente por `Any`.

Los argumentos son `List<TypeProjection>`. `TypeProjection.Star` representa `*`; `TypeProjection.Typed` contiene el tipo y su `KVariance`, que puede ser `INVARIANT`, `IN` u `OUT`.

```kotlin
// Array<out Number?>
TypeDescriptor(
    classifier = Array::class,
    typeArguments = listOf(
        TypeProjection.Typed(
            type = TypeDescriptor(Number::class, nullability = TypeNullability.NULLABLE),
            variance = KVariance.OUT,
        ),
    ),
)
```

Las estrellas conservan su posición: `Map<*, String>` tiene dos argumentos. No se representan con `Any?`, porque una proyección estrella no es un tipo concreto.

## Tipos dynamic de JavaScript

```kotlin
val dynamic = TypeDescriptor(kind = TypeKind.DYNAMIC)
// dynamic.classifier, dynamic.typeParameter y dynamic.nullability son null.
```

Las anotaciones del parámetro y las del tipo permanecen separadas: en `fun read(@Argument value: @Sensitive dynamic)`, la primera pertenece a ParameterDescriptor.annotations y la segunda a TypeDescriptor.annotations.

El generador reconoce KSDynamicReference antes de resolver un clasificador y conserva referencias fuente en funciones y argumentos genéricos: `(dynamic) -> dynamic` y `List<dynamic>` no se describen como Any con nulabilidad PLATFORM. Kotlin/Wasm no admite dynamic: estas pruebas se colocan en jsTest y se ejecutan en Node y navegador.

## Parámetros genéricos y restricciones

`TypeParameterDescriptor` contiene nombre, identidad, varianza declarada y todos los límites superiores. Las restricciones escritas con `where` y con `<T : Bound>` tienen la misma representación semántica.

```kotlin
interface Repository<T> where T : CharSequence, T : Comparable<T>
```

El parámetro de este ejemplo tiene dos límites. Dentro de `Comparable<T>`, el argumento referencia `T` mediante `TypeParameterId`, sin expandir sus límites nuevamente. Así se evitan ciclos de objetos e inicializaciones recursivas.

La identidad combina declaración propietaria e índice. Para funciones se incluye su firma de parámetros para distinguir sobrecargas. El nombre `T` por sí solo no es una identidad. La representación textual de firmas depende de KSP; estos identificadores son metadatos internos de la compilación y no un formato persistente entre versiones.

La varianza declarada (`Source<out T>`) pertenece al parámetro. Las proyecciones de uso (`MutableList<out User>`) pertenecen a los argumentos. El generador conserva las firmas genéricas de las clases y métodos utilizando el resolvedor de parámetros de KotlinPoet, incluidos los límites múltiples.

Los registros construyen las implementaciones genéricas utilizando `Nothing` como argumento interno y realizan el cast del proxy en una única frontera. Esto no especializa los metadatos según los argumentos de una llamada a `create`: `KClass` solo identifica la interfaz.

## Nulabilidad

`nullability` es la única fuente de verdad para los tipos clasificador y parámetro. Para `DYNAMIC` es null, porque dynamic desactiva las comprobaciones estáticas y no representa un estado de nulabilidad. No hay un booleano almacenado adicional que pueda contradecirla. Distingue:

| Valor | Significado |
| --- | --- |
| `NOT_NULL` | Excluye null, tanto en `String` como en `T & Any` |
| `NULLABLE` | Tipo marcado nullable |
| `UNMARKED` | La nulabilidad depende del parámetro y sus restricciones |
| `PLATFORM` | KSP expone nulabilidad de plataforma procedente de interoperabilidad |


`T` sin interrogación no implica que el argumento concreto sea no nullable. Tampoco se calcula automáticamente un resultado de subtipado a partir de los límites. Cada nivel de un tipo anidado conserva su propia nulabilidad, como en `List<List<String?>?>`.

Para `T & Any`, el procesador reconoce `KSDefNonNullReference`, emite la firma de intersección y utiliza paréntesis en los casts. KotlinPoet 1.18 no conserva esta forma automáticamente; el generador construye el nombre de la intersección utilizando el parámetro declarado.

### Tipos ordinarios y tipos definitivamente no nulos

`String` no admite null y utiliza `NOT_NULL`. `T & Any` también excluye null, pero representa la parte no nula de un parámetro genérico cuyo límite superior puede ser nullable. Para `T = String?`, `T & Any` se comporta como `String`. No modifica el parametro T para sus otros usos ni obliga a declarar `T : Any` en toda la función.

Ambas formas utilizan `NOT_NULL`. La referencia a la clase o al parámetro conserva su identidad por separado. Un uso ordinario de T, incluso si tiene un límite `T : Any`, utiliza `UNMARKED`: sus restricciones determinan la nulabilidad. La consulta effectiveNullability(scope) obtiene garantías de los bounds; véase [Operaciones de tipos](TYPE_OPERATIONS.md).

No se introduce una extensión `isNullable` porque un booleano ambiguo ocultaría `UNMARKED` y `PLATFORM`. Si se necesita una consulta de conveniencia, deberá expresar una pregunta precisa, por ejemplo si la forma declarada es `NULLABLE`.

## Funciones y receptores

Los tipos función guardan `FunctionTypeFlags(isSuspend, hasReceiver)` y una sola lista de tipos en `typeArguments`. Los flags están presentes también para `(Int) -> String`, aunque ambos booleanos sean false. `functionType` es una vista calculada: no hay otra copia de los parámetros, receptor o retorno que pueda quedar desincronizada.

Con flags, `classifier` es siempre `Function::class`, un marcador lógico común. Las posiciones son receptor opcional, parámetros ordinarios y retorno. Nunca se añade un `Continuation` implícito. Esto es igual para funciones normales y suspendidas, incluidas las de 23 parámetros.

```kotlin
val callback = TypeDescriptor.function(
    parameters = listOf(TypeProjection.Typed(TypeDescriptor(Int::class))),
    returnType = TypeProjection.Typed(TypeDescriptor(Boolean::class)),
    receiverType = TypeProjection.Typed(TypeDescriptor(String::class)),
    isSuspend = true,
) // suspend String.(Int) -> Boolean

check(callback.classifier == Function::class)
check(callback.functionFlags == FunctionTypeFlags(isSuspend = true, hasReceiver = true))
check(callback.typeArguments.size == 3)
check(callback.functionType!!.parameters.size == 1)
```

El marcador no identifica la clase real del callback. `Function<R>` sin flags sigue siendo un clasificador corriente con un argumento. Los tipos reflexivos `KFunction1` y `KSuspendFunction1`, las interfaces SAM y una clase propia llamada Function1 conservan su propio clasificador sin flags ni vista `functionType`.

### Clasificación en el generador

Una única función `kotlinFunctionFamily` compara el nombre cualificado de la declaración KSP con las familias exactas `kotlin.Function<N>` y `kotlin.coroutines.SuspendFunction<N>`, sin límite de aridad. No interpreta nombres de KClass en runtime. Se realiza después de expandir los alias.

En la API pública de KSP 2.0.21-1.0.28 inspeccionada no existe otro indicador de familia. Sus propiedades `isFunctionType` e `isSuspendFunctionType` también reconocen tipos reflexivos; no sirven por sí solas para esta política. Las pruebas de caracterización verifican ese comportamiento por separado del contrato público de KProxyable.

### Construcción y normalización

El constructor de bajo nivel y `copy()` validan juntos estos invariantes:

- Los flags solo pertenecen a CLASSIFIER con `classifier == Function::class`.
- Tiene que existir una posición de retorno y, si hasReceiver es true, una de receptor.
- Cada posición es Star o Typed con varianza INVARIANT.

La fábrica admite proyecciones: normaliza `in` en entradas y `out` en el resultado a INVARIANT, y rechaza las proyecciones contrarias. La presencia del receptor determina hasReceiver. Las estrellas mantienen su posición y los flags, pero `functionType` devuelve null si hay alguna: no inventa una firma parcial.

```kotlin
val unknownInput = TypeDescriptor.function(
    parameters = listOf(TypeProjection.Star),
    returnType = TypeProjection.Typed(TypeDescriptor(String::class)),
)
check(unknownInput.functionFlags != null)
check(unknownInput.functionType == null)
```

El generador utiliza la misma fábrica. Por ello una flecha, `Function1<Int, String>`, su variante con proyecciones redundantes, un alias equivalente y la fábrica producen el mismo descriptor. La igualdad sigue siendo estructural: el constructor crudo permite describir un Function1 sin flags, y no promete deducir o normalizar su significado a partir de un token de runtime. La lista de anotaciones utiliza la igualdad que proporcionan sus instancias en cada backend; estas reglas no prometen equivalencia semántica general entre tipos Kotlin.

`kotlin.ExtensionFunctionType` se transforma en hasReceiver en cada nodo función. `kotlin.ParameterName` se elimina solo del tipo de sus entradas directas, incluido el receptor. El proceso se repite en nodos función anidados. En `List<@ParameterName("element") Int>` se conserva: Int es argumento de una colección ordinaria. Las demás anotaciones de usuario permanecen.

### Valores que recibe el handler

El handler recibe el objeto callback original; el descriptor representa su firma lógica. Para invocar un callback suspendido se utiliza su tipo suspend real:

```kotlin
@Suppress("UNCHECKED_CAST")
val callback = args.single() as suspend String.(Int) -> Boolean
val result = callback("example", 3) // Desde un contexto suspend
```

La suspensión del método interceptado se expone por separado en `FunctionDescriptor.isSuspend`. Un método normal puede recibir una función suspendida sin ser él mismo suspendido.

Los métodos de extensión de una interfaz conservan su receptor en la firma y en `receiverType`. El receptor se entrega como primer elemento de args, antes de los parámetros ordinarios. `parameters` describe solamente estos últimos.

Las propiedades de extensión requieren ampliar el handler para transportar su receptor; siguen rechazándose en el generador. PropertyDescriptor ya no contiene receiverType y su owner es obligatorio.

## Anotaciones e información de interfaz

Se generan instancias Kotlin de las anotaciones de funciones, parámetros, propiedades y tipos que expone KSP. Las anotaciones sin argumentos requieren paréntesis al convertirse de sintaxis de anotación a expresión constructora. No se utiliza reflexión de runtime.

`InterfaceDescriptor` conserva la clase, sus parámetros de tipo y anotaciones. `FunctionDescriptor.owner` es obligatorio y no nullable. Los descriptores de miembros contienen `owner` con la interfaz para la que se genera el proxy. No es necesariamente la interfaz original que declaró el miembro heredado.

Las anotaciones sobre tipos y las de parámetros no se mezclan: `List<@Sensitive String>` conserva la anotación en el descriptor de `String`. La visibilidad de la anotación y sus argumentos debe permitir referenciarlos desde el código generado.

## Alias y clases valor

Los alias se expanden a su tipo subyacente con sustitución de argumentos y propagación de nulabilidad. Por ejemplo, `typealias Names<T> = List<T?>` aplicado a `String` produce `List<String?>`. El nombre del alias no se utiliza como identidad de tipo.

Las clases valor mantienen su `KClass`; no se expanden al tipo de su campo. Un alias de `String` y una clase valor que envuelve `String` no tienen la misma semántica.

## Herencia y generación de firmas

El procesador recorre los miembros de la jerarquía y utiliza `asMemberOf` para resolver los tipos efectivos. En `Users : Repository<User>`, los miembros heredados utilizan `User`. Las declaraciones genéricas conservan referencias simbólicas a sus parámetros cuando todavía no hay un argumento concreto.

Las implementaciones de `Any` se generan por separado para evitar duplicados. Los nombres de descriptores de funciones sobrecargadas siguen teniendo un sufijo. Los parámetros vararg mantienen `vararg` en la firma y llegan al handler como un único array, sin aplanarse en varios argumentos.

## Compatibilidad y migración

Este cambio rompe compatibilidad de fuentes y binaria del modelo de tipos. Todos los módulos que publican o consumen proxies deben recompilarse con versiones alineadas del runtime y procesador. No debe publicarse como un parche compatible.

```kotlin
// Antes
val element = type.typeArguments.single()
// Ahora
val projection = type.typeArguments.single()
val element = (projection as? TypeProjection.Typed)?.type
// null significa que hay una estrella, no que sea Any?.
```

`classifier` es nullable porque un parámetro genérico y dynamic no tienen un clasificador propio. El antiguo estado TypeNullability.PARAMETER se renombra UNMARKED; TypeKind.PARAMETER describe la forma del tipo, no su nulabilidad. TypeDescriptor.nullability ahora es nullable únicamente para kind DYNAMIC. Los consumidores deben consultar `typeParameter` cuando corresponda. Las llamadas sencillas `TypeDescriptor(String::class)` siguen siendo válidas.

Para tipos función, `functionType` deja de ser un argumento del constructor: es una propiedad calculada. Utiliza `TypeDescriptor.function(...)` para construirlos o reconstruirlos. Las comparaciones con `Function1::class` se sustituyen por la consulta de functionFlags y las posiciones lógicas. KFunctionN y KSuspendFunctionN dejan de exponer functionType; conservan su identidad y argumentos de clasificador ordinario. Function<R> sin flags mantiene su representación anterior.

La sustitución simultánea, scopes, nulabilidad efectiva, normalización según bounds y cambios de PropertyDescriptor están implementados y descritos en [Operaciones de tipos](TYPE_OPERATIONS.md).


## Cambios del registro necesarios para los tests

Los proxies declarados en fuentes de test se agregan a registros con sufijo `_Test`, sin sobrescribir los de main. El procesador reconoce rutas de source sets `*Test`; también admite la opción explícita `kproxyable.isTest`. Para directorios personalizados debe proporcionarse esa opción.

Los breadcrumbs de test de dependencias se excluyen del descubrimiento. El registro compuesto de test agrega su propio registro local y el de main. La suite define un registro expect exclusivo de test para comprobar esta composición sin depender de que el registro de main descubra interfaces compiladas posteriormente.

## Trabajo adicional y condiciones de aceptación

Las siguientes capacidades se separan de la representación básica y requieren implementación o cobertura adicional antes de anunciar soporte completo:

| Capacidad | Implementación necesaria | Verificación |
| --- | --- | --- |
| Especializar `Service<User>` al crear el proxy | API de creación que reciba el tipo completo y entorno de sustituciones por instancia | Dos especializaciones simultáneas sin compartir descriptores especializados |
| Identidad persistente de miembros | Firma canónica independiente de `toString` de KSP, incluyendo receptor y propietario | Sobrecargas y extensiones con igual nombre |
| Enumerar miembros sin invocar | API `describe` en registro y colección de miembros en interfaz | Validación anticipada sin crear ni invocar el proxy |
| Metaanotaciones de usuario | Descriptor explícito o índice generado de anotaciones calificadoras | Seleccionar un calificador sin consultar reflexión |
| Propiedades de extensión | Contrato de handler que transporte receptor en getter y setter | Lectura y escritura con dos receptores distintos |
| Tipos internos con argumentos del exterior | Representación explícita del tipo propietario y orden de argumentos | `Outer<String>.Inner<Int>` en cada plataforma |
| Interoperabilidad Java completa | Conservar límites flexibles y raw types, además de `PLATFORM` | Fixtures Java de herencia y colecciones |
| Use-site targets de anotaciones | Asociar getter, setter, campo y propiedad por separado al construir expresiones | Propiedades anotadas en cada destino y compilación en todos los targets |
| Intersecciones anidadas | Propagar `KSDefNonNullReference` también por todos los argumentos anidados | `List<T & Any>` y alias parametrizados |
| Versiones adicionales de Kotlin y KSP | Ejecutar la matriz de versiones del repositorio | Compilación y suite en cada combinación soportada |

El criterio general es conservar la información o emitir un diagnóstico, nunca sustituirla por un tipo aproximado sin avisar. Las capacidades de esta tabla no deben inferirse a partir de que un caso simple compile.

## Pruebas y ejecución

`sample-kmp/src/commonTest/.../TypeMetadataTest.kt` contiene fixtures reales anotadas con `KProxyable`. KSP genera los proxies, el compilador comprueba sus overrides y las pruebas invocan los proxies mediante el registro. Se comprueban descriptores y argumentos recibidos por un handler real.

Los tests de jsTest comprueban dynamic con anotaciones independientes en parametro y tipo, retornos, propiedades, callbacks y argumentos genericos. La cobertura comun incluye invariantes del modelo, varianza declarada, parámetros que ocultan otros, clases valor, arrays primitivos, receptores de extensión, anotaciones con arrays, enums y valores anidados, proyecciones, estrellas, anotaciones, nulabilidad anidada, alias simples y genéricos, tipos función suspendidos con receptor, límites múltiples y recursivos, identidad de parámetros, `T & Any`, herencia de métodos y propiedades, sobrecargas, varargs e invocaciones suspendidas. Las pruebas previas de enlace entre módulos se ejecutan conjuntamente.

```powershell
.\gradlew.bat :sample-common:jvmTest :sample-kmp:jvmTest `
    :sample-kmp:jsNodeTest :sample-kmp:wasmJsNodeTest `
    :sample-kmp:mingwX64Test '-Pkproxyable.matrix=true'
```

Native se ejecuta en Windows mediante mingwX64. Las plataformas Apple necesitan un host macOS para sus compilaciones y tests. Una ejecución en estos cuatro targets no sustituye la matriz completa de versiones.

## Referencias

- [Proyecciones de Kotlin](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.reflect/-k-type-projection/)

- [Genéricos y restricciones](https://kotlinlang.org/docs/generics.html)

- [Tipos función](https://kotlinlang.org/docs/lambdas.html)

- [Alias](https://kotlinlang.org/docs/type-aliases.html)

- [Interoperabilidad Java](https://kotlinlang.org/docs/java-interop.html)

## Verificación del contrato de funciones

El [informe del sondeo](FUNCTION_TYPE_PROBE.md) conserva los hallazgos previos y explica cómo se mantienen las comprobaciones directas de KSP. Las suites actuales separan:

- `KotlinFunctionFamilyTest`: política de nombres cualificados exactos del procesador.
- `KspFunctionCharacterizationTest`: observaciones de símbolos KSP sin normalizar, generadas por un procesador exclusivo de tests en cada target.
- `FunctionTypeContractTest`: metadatos entregados al handler por proxies reales, incluidas aridad 23 normal y suspend, alias genéricos, reflexivos y estrellas.
- `FunctionDescriptorFactoryTest`: construcción manual, normalización de anotaciones/proyecciones y rechazo de estados inválidos mediante constructor y copy.

Las pruebas anteriores de tipos, dynamic y enlace entre módulos se ejecutan conjuntamente. Probado con Kotlin 2.0.21 y KSP 2.0.21-1.0.28; esa combinación comprobada no establece por sí sola la versión mínima soportada.

Windows:

```powershell
$env:CHROME_BIN = 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'
.\gradlew.bat jvmTest jsNodeTest wasmJsNodeTest mingwX64Test test `
    jsBrowserTest wasmJsBrowserTest :kproxyable-gradle-plugin:test `
    --continue '-Pkproxyable.matrix=true'
```

Native Linux x64, mediante Ubuntu WSL:

```bash
bash ./gradlew linuxX64Test --continue --max-workers=1 \
    --project-cache-dir build/wsl-gradle-cache \
    -Pkproxyable.matrix=true -Pkotlin.native.disableCompilerDaemon=true
```

La opción de Native evita un cierre de JVM observado con el compilador en el daemon en este equipo; no modifica la configuración persistente del proyecto. Linux ARM64 y Apple no se ejecutan en este host. La matriz adicional de versiones de Kotlin/KSP tampoco está incluida en esta comprobación.

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
