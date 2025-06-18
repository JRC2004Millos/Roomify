using UnityEngine;
using UnityEngine.UI;
using UnityEngine.EventSystems;

/// <summary>
/// Script de movimiento del usuario con soporte para PC (teclado y mouse)
/// y móviles (joystick y botón de salto).
/// </summary>
public class UserMovement : MonoBehaviour
{
    public float speed = 1.0f;           // Velocidad de movimiento
    public float rotationSpeed = 1.0f;   // Velocidad de rotación con el mouse
    public float jumpForce = 1.0f;       // Fuerza del salto
    private Rigidbody physics;           // Componente Rigidbody

    // === ENTRADAS PARA MÓVIL ===
    public SimpleJoystick joystick;      // Referencia al joystick móvil
    public Button jumpButton;            // Botón de salto móvil
    private bool jumpRequested = false;  // Bandera para salto desde botón móvil

    void Start()
    {
        // Bloquear cursor (solo en PC)
#if UNITY_STANDALONE || UNITY_EDITOR
        Cursor.lockState = CursorLockMode.Locked;
        Cursor.visible = false;
#endif

        physics = GetComponent<Rigidbody>();

        // Asignar evento de salto al botón si está presente
        if (jumpButton != null)
        {
            jumpButton.onClick.AddListener(JumpMobile);
        }
    }

    void Update()
    {
        // === ENTRADAS DE MOVIMIENTO ===
        float moveHorizontal = Input.GetAxis("Horizontal");
        float moveVertical = Input.GetAxis("Vertical");

        // Si hay joystick conectado y hay input móvil, sobrescribir entradas
        if (joystick != null)
        {
            if (Mathf.Abs(joystick.Horizontal()) > 0.05f || Mathf.Abs(joystick.Vertical()) > 0.05f)
            {
                moveHorizontal = joystick.Horizontal();
                moveVertical = joystick.Vertical();
            }
        }

        Vector3 movement = new Vector3(moveHorizontal, 0.0f, moveVertical);
        transform.Translate(movement * speed * Time.deltaTime, Space.World);

        // === ROTACIÓN (solo para PC) ===
#if UNITY_STANDALONE || UNITY_EDITOR
        float rotationY = Input.GetAxis("Mouse X");
        transform.Rotate(new Vector3(0, rotationY * Time.deltaTime * rotationSpeed, 0));
#endif

        // === SALTO ===

        // Tecla espacio (PC)
        if (Input.GetKeyDown(KeyCode.Space) && Mathf.Abs(physics.linearVelocity.y) < 0.01f)
        {
            physics.AddForce(Vector3.up * jumpForce, ForceMode.Impulse);
        }

        // Botón de salto (Mobile)
        if (jumpRequested && Mathf.Abs(physics.linearVelocity.y) < 0.01f)
        {
            physics.AddForce(Vector3.up * jumpForce, ForceMode.Impulse);
            jumpRequested = false;
        }
    }

    /// <summary>
    /// Se llama desde el botón en móviles.
    /// </summary>
    public void JumpMobile()
    {
        jumpRequested = true;
    }
}
