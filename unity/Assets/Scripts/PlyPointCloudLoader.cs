using System.Collections.Generic;
using System.Globalization;
using System.IO;
using UnityEngine;

public class PlyPointCloudLoader : MonoBehaviour
{
    public string filePath = "C:/Users/Usuario/Desktop/RoomifyPruebas/keypoints.ply";
    public float scale = 0.01f;
    public GameObject pointPrefab; // Asigna una esfera pequeña

    void Start()
    {
        LoadPLY();
    }

    void LoadPLY()
    {
        if (!File.Exists(filePath))
        {
            Debug.LogError("Archivo no encontrado: " + filePath);
            return;
        }

        List<Vector3> puntos = new List<Vector3>();
        int vertexCount = 0;
        bool headerEnded = false;

        foreach (string line in File.ReadLines(filePath))
        {
            if (!headerEnded)
            {
                if (line.StartsWith("element vertex"))
                {
                    var parts = line.Split(' ');
                    vertexCount = int.Parse(parts[2]);
                }
                else if (line == "end_header")
                {
                    headerEnded = true;
                }
                continue;
            }

            if (vertexCount-- > 0)
            {
                var parts = line.Split(' ');
                if (parts.Length >= 3)
                {
                    float x = float.Parse(parts[0], CultureInfo.InvariantCulture);
                    float y = float.Parse(parts[1], CultureInfo.InvariantCulture);
                    float z = float.Parse(parts[2], CultureInfo.InvariantCulture);
                    puntos.Add(new Vector3(x, y, z));
                }
            }
        }
        Debug.Log($"✅ {puntos.Count} puntos cargados desde el .ply");

        foreach (var punto in puntos)
        {
            var instancia = Instantiate(pointPrefab, punto * scale, Quaternion.identity, transform);
            Debug.Log($"🟢 Punto instanciado en: {instancia.transform.position}");
        }
    }
}
