using System.Collections.Generic;
using UnityEngine;
using TMPro;
using System.IO;

public class LineManager : MonoBehaviour
{
    [Header("Refs")]
    public LineRenderer lineRenderer;
    public ARPlacementController placementController;
    public TextMeshProUGUI debugText;
    public TextMeshProUGUI TxtEstado;
    public TextMeshPro mText; // prefab TMP (WorldSpace)

    public InstructionHUD hud;

    // --- Estado general ---
    private List<Vector3> placedPositions = new List<Vector3>(); // piso (Vector3 para dibujar)
    private List<Corner> corners = new List<Corner>();           // piso (cada corner -> Vector2 (x,z))
    private char currentCornerId = 'A';

    public enum ModoMedicion { Altura, Piso }
    public ModoMedicion modoActual = ModoMedicion.Altura;

    // --- Altura ---
    private List<Vector3> puntosAltura = new List<Vector3>(); // 0..2
    private float alturaMedida = 2.8f;

    // --- “Desde última confirmación” (para BtnEliminar) ---
    private int placedPositionsAtConfirm = 0;
    private int cornersAtConfirm = 0;
    private char cornerIdAtConfirm = 'A';
    private List<GameObject> visualsSinceConfirm = new List<GameObject>(); // textos, líneas temporales, etc.

    void Start()
    {
        if (placementController != null)
            placementController.OnObjectPlaced += DrawLine;

        // Mensaje inicial
        TxtEstado?.SetText("Paso 1: Marca dos puntos para medir la altura. Luego confirma.");
        debugText?.SetText("Modo: Altura");
        hud?.SetModeAltura();
        SnapshotAtConfirm(); // snapshot “vacío” al inicio
    }

    void OnDestroy()
    {
        if (placementController != null)
            placementController.OnObjectPlaced -= DrawLine;
    }

    // -----------------------
    // Entrada principal (tap)
    // -----------------------
    void DrawLine(Vector3 newPoint)
    {
        if (modoActual == ModoMedicion.Altura)
        {
            // Asegurar flujo limpio en altura
            if (puntosAltura.Count == 0)
                ClearVisualsSinceConfirm();

            if (puntosAltura.Count >= 2)
            {
                // Ignora taps extra hasta que confirme o elimine
                TxtEstado?.SetText("Ya marcaste 2 puntos de altura. Presiona Confirmar o Eliminar.");
                return;
            }

            puntosAltura.Add(newPoint);
            hud?.OnHeightPointsChanged(puntosAltura.Count);

            if (puntosAltura.Count == 1)
            {
                debugText?.SetText("Punto inferior de altura colocado.");
            }
            else if (puntosAltura.Count == 2)
            {
                float altura = Vector3.Distance(puntosAltura[0], puntosAltura[1]);
                alturaMedida = altura;
                debugText?.SetText($"Altura medida: {(altura * 100f):F1} cm");

                // Línea temporal de altura
                var go = new GameObject("AlturaLine");
                var lr = go.AddComponent<LineRenderer>();
                lr.material = lineRenderer.material;
                lr.widthMultiplier = lineRenderer.widthMultiplier;
                lr.positionCount = 2;
                lr.SetPosition(0, puntosAltura[0]);
                lr.SetPosition(1, puntosAltura[1]);
                visualsSinceConfirm.Add(go);

                // Texto de altura
                TextMeshPro distText = Instantiate(mText);
                distText.text = $"{(altura * 100f):F1} cm";
                Vector3 mid = (puntosAltura[0] + puntosAltura[1]) * 0.5f + Vector3.right * 0.05f;
                distText.transform.position = mid;
                if (Camera.main != null)
                    distText.transform.rotation = Quaternion.LookRotation(-Camera.main.transform.forward);
                visualsSinceConfirm.Add(distText.gameObject);

                TxtEstado?.SetText("Altura lista. Presiona Confirmar para pasar a medir el piso.");
            }

            return;
        }

        // ----- Modo Piso -----
        placedPositions.Add(newPoint);
        debugText?.SetText($"Punto añadido: {newPoint}");
        hud?.OnFloorPointsChanged(placedPositions.Count);

        corners.Add(new Corner(currentCornerId.ToString(), newPoint));
        currentCornerId++;

        // dibujar último punto
        lineRenderer.positionCount++;
        lineRenderer.SetPosition(lineRenderer.positionCount - 1, newPoint);

        if (lineRenderer.positionCount > 1)
        {
            Vector3 pointA = lineRenderer.GetPosition(lineRenderer.positionCount - 2);
            Vector3 pointB = lineRenderer.GetPosition(lineRenderer.positionCount - 1);
            float dist = Vector3.Distance(pointA, pointB);

            TextMeshPro distText = Instantiate(mText);
            distText.text = $"{(dist * 100f):F1} cm";

            Vector3 directionVector = (pointB - pointA).normalized;
            Vector3 normal = Vector3.up;
            Vector3 upward = Vector3.Cross(directionVector, -normal).normalized;

            Quaternion rotation = Quaternion.LookRotation(-normal, upward);
            Vector3 midPoint = pointA + directionVector * 0.5f;
            Vector3 elevation = upward * 0.02f;

            distText.transform.position = midPoint + elevation;
            distText.transform.rotation = rotation;

            visualsSinceConfirm.Add(distText.gameObject);
        }
    }

    // -----------------------
    // Botón: Confirmar
    // -----------------------
    public void OnBtnConfirmar()
    {
        if (modoActual == ModoMedicion.Altura)
        {
            if (puntosAltura.Count < 2)
            {
                TxtEstado?.SetText("Debes marcar dos puntos para medir la altura.");
                return;
            }

            // Confirmar altura y pasar a piso
            modoActual = ModoMedicion.Piso;
            TxtEstado?.SetText("Paso 2: Mide las esquinas del piso. Agrega al menos 3 puntos y confirma.");
            debugText?.SetText($"Altura confirmada: {(alturaMedida * 100f):F1} cm");
            hud?.SetModePiso();

            // limpiar visuales de altura para empezar piso limpio
            ClearVisualsSinceConfirm();
            puntosAltura.Clear();

            // snapshot para “eliminar”
            SnapshotAtConfirm();
            return;
        }

        if (modoActual == ModoMedicion.Piso)
        {
            if (placedPositions.Count < 3)
            {
                TxtEstado?.SetText("Debes tener al menos 3 puntos para cerrar el cuarto.");
                return;
            }

            // Cerrar visualmente
            CerrarFiguraVisual();

            TxtEstado?.SetText("Figura cerrada. Generando JSON...");
            string path = ExportarJSON();
            hud?.ShowExportResult(path);
            TxtEstado?.SetText("JSON generado. Si quieres, puedes empezar otra medición.");

            // snapshot después de confirmar piso (por si el flujo continúa)
            SnapshotAtConfirm();
        }
    }

    // -----------------------
    // Botón: Eliminar (undo soft)
    // -----------------------
    public void OnBtnEliminar()
    {
        // Borra todo lo creado desde la última confirmación (datos y visuales)
        ClearVisualsSinceConfirm();

        if (modoActual == ModoMedicion.Altura)
        {
            puntosAltura.Clear();
            TxtEstado?.SetText("Altura: se borraron los puntos desde la última confirmación.");
            return;
        }

        if (modoActual == ModoMedicion.Piso)
        {
            // revertir puntos y corners a snapshot
            if (placedPositions.Count > placedPositionsAtConfirm)
                placedPositions.RemoveRange(placedPositionsAtConfirm, placedPositions.Count - placedPositionsAtConfirm);

            if (corners.Count > cornersAtConfirm)
                corners.RemoveRange(cornersAtConfirm, corners.Count - cornersAtConfirm);

            currentCornerId = cornerIdAtConfirm;

            // reconstruir lineRenderer con los puntos confirmados
            lineRenderer.positionCount = 0;
            for (int i = 0; i < placedPositions.Count; i++)
            {
                lineRenderer.positionCount++;
                lineRenderer.SetPosition(lineRenderer.positionCount - 1, placedPositions[i]);
            }

            TxtEstado?.SetText("Piso: se borraron puntos y textos desde la última confirmación.");
        }
    }

    // Cierra figura SOLO visualmente (no duplica corner en la data)
    private void CerrarFiguraVisual()
    {
        if (placedPositions.Count >= 3)
        {
            // Dibujar segmento final: último -> primero (solo en renderer)
            var first = placedPositions[0];
            lineRenderer.positionCount++;
            lineRenderer.SetPosition(lineRenderer.positionCount - 1, first);

            // Distancia del último tramo
            Vector3 A = placedPositions[placedPositions.Count - 1];
            Vector3 B = first;
            float dist = Vector3.Distance(A, B);

            TextMeshPro distText = Instantiate(mText);
            distText.text = $"{(dist * 100f):F1} cm";

            Vector3 directionVector = (B - A).normalized;
            Vector3 normal = Vector3.up;
            Vector3 upward = Vector3.Cross(directionVector, -normal).normalized;

            Quaternion rotation = Quaternion.LookRotation(-normal, upward);
            Vector3 midPoint = A + directionVector * 0.5f;
            Vector3 elevation = upward * 0.02f;

            distText.transform.position = midPoint + elevation;
            distText.transform.rotation = rotation;

            visualsSinceConfirm.Add(distText.gameObject);

            debugText?.SetText("Figura cerrada.");
        }
    }

    public string ExportarJSON()
    {
        RoomData data = new RoomData
        {
            room_dimensions = new RoomDimensions { height = alturaMedida },
            corners = corners,
            walls = new List<Wall>(),
            obstacles = new List<Obstacle>()
        };

        // Generar paredes con índice circular (i -> i+1, cerrando hacia el 0)
        for (int i = 0; i < corners.Count; i++)
        {
            int j = (i + 1) % corners.Count;

            // Asumiendo Corner.position es Vector2 (x,z) mapeado en (x,y) del Vector2:
            Vector2 fromPos = corners[i].position;
            Vector2 toPos = corners[j].position;

            // Si Corner.position fuera Vector3, reemplaza las dos líneas de arriba por:
            // Vector2 fromPos = new Vector2(corners[i].position.x, corners[i].position.z);
            // Vector2 toPos   = new Vector2(corners[j].position.x, corners[j].position.z);

            float distance = Vector2.Distance(fromPos, toPos);
            string dir = CalcularDireccion(fromPos, toPos);

            data.walls.Add(new Wall
            {
                from = corners[i].id,
                to = corners[j].id,
                distance = distance,
                direction = dir
            });
        }

        string json = JsonUtility.ToJson(data, true);
        string path = Path.Combine(Application.persistentDataPath, "room_data.json");
        File.WriteAllText(path, json);

        debugText?.SetText($"Archivo exportado:\n{path}");
        Debug.Log("JSON generado en: " + path);

        return path;
    }

    private string CalcularDireccion(Vector2 from, Vector2 to)
    {
        Vector2 dir = (to - from).normalized;
        if (Mathf.Abs(dir.x) > Mathf.Abs(dir.y))
            return dir.x > 0 ? "east" : "west";
        else
            return dir.y > 0 ? "north" : "south";
    }

    // -----------------------
    // Utilidades
    // -----------------------
    private void SnapshotAtConfirm()
    {
        placedPositionsAtConfirm = placedPositions.Count;
        cornersAtConfirm = corners.Count;
        cornerIdAtConfirm = currentCornerId;

        ClearVisualsSinceConfirm(); // arrancar “limpio” el nuevo tramo
    }

    private void ClearVisualsSinceConfirm()
    {
        // Destruye textos y líneas temporales creadas desde la última confirmación
        for (int i = visualsSinceConfirm.Count - 1; i >= 0; i--)
        {
            var go = visualsSinceConfirm[i];
            if (go != null) Destroy(go);
        }
        visualsSinceConfirm.Clear();

        // (No toca el lineRenderer de lo ya confirmado)
        // Si quieres limpiar TODO el lineRenderer, haz:
        // lineRenderer.positionCount = 0; (pero eso borraría también lo confirmado)
    }
}
