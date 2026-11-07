package com.giro.servicio;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

final class PdfTexto {
    private static final Charset LATIN = Charset.forName("ISO-8859-1");
    private static final int ANCHO = 95;
    private final List<StringBuilder> paginas = new ArrayList<>();
    private StringBuilder actual = new StringBuilder();
    private int lineas;

    PdfTexto() {
        paginas.add(actual);
    }

    void titulo(String texto) {
        actual.append("/F1 16 Tf\n");
        escribir(texto);
        actual.append("/F1 10 Tf\n");
    }

    void subtitulo(String texto) {
        actual.append("/F1 12 Tf\n");
        escribir(texto);
        actual.append("/F1 10 Tf\n");
    }

    void linea(String texto) {
        for (String parte : cortar(texto == null ? "" : texto)) escribir(parte);
    }

    void blanco() {
        escribir(" ");
    }

    byte[] bytes() {
        List<byte[]> contenidos = new ArrayList<>();
        for (StringBuilder pagina : paginas) {
            contenidos.add(("BT\n40 810 Td\n" + pagina + "ET\n").getBytes(LATIN));
        }
        int paginasTotal = contenidos.size();
        int fuente = 3 + paginasTotal * 2;
        int objetos = fuente;
        int[] xref = new int[objetos + 1];
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes("%PDF-1.4\n".getBytes(LATIN));
        objeto(out, xref, 1, "<< /Type /Catalog /Pages 2 0 R >>");
        StringBuilder hijos = new StringBuilder();
        for (int i = 0; i < paginasTotal; i++) {
            if (i > 0) hijos.append(' ');
            hijos.append(3 + i * 2).append(" 0 R");
        }
        objeto(out, xref, 2, "<< /Type /Pages /Kids [" + hijos + "] /Count " + paginasTotal + " >>");
        for (int i = 0; i < paginasTotal; i++) {
            int pagina = 3 + i * 2;
            int contenido = pagina + 1;
            objeto(out, xref, pagina, "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Contents " + contenido + " 0 R /Resources << /Font << /F1 " + fuente + " 0 R >> >> >>");
            xref[contenido] = out.size();
            byte[] stream = contenidos.get(i);
            out.writeBytes((contenido + " 0 obj\n<< /Length " + stream.length + " >>\nstream\n").getBytes(LATIN));
            out.writeBytes(stream);
            out.writeBytes("endstream\nendobj\n".getBytes(LATIN));
        }
        objeto(out, xref, fuente, "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>");
        int inicio = out.size();
        out.writeBytes(("xref\n0 " + (objetos + 1) + "\n").getBytes(LATIN));
        out.writeBytes("0000000000 65535 f \n".getBytes(LATIN));
        for (int i = 1; i <= objetos; i++) {
            out.writeBytes(String.format("%010d 00000 n \n", xref[i]).getBytes(LATIN));
        }
        out.writeBytes(("trailer\n<< /Size " + (objetos + 1) + " /Root 1 0 R >>\nstartxref\n" + inicio + "\n%%EOF").getBytes(LATIN));
        return out.toByteArray();
    }

    private void escribir(String texto) {
        if (lineas >= 46) {
            actual = new StringBuilder();
            actual.append("/F1 10 Tf\n");
            paginas.add(actual);
            lineas = 0;
        }
        actual.append('(').append(escapar(texto)).append(") Tj\n0 -15 Td\n");
        lineas++;
    }

    private List<String> cortar(String texto) {
        String limpio = texto.replace('\n', ' ').replace('\r', ' ');
        List<String> partes = new ArrayList<>();
        if (limpio.isEmpty()) {
            partes.add("");
            return partes;
        }
        for (int i = 0; i < limpio.length(); i += ANCHO) {
            partes.add(limpio.substring(i, Math.min(i + ANCHO, limpio.length())));
        }
        return partes;
    }

    private String escapar(String texto) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < texto.length(); i++) {
            char c = texto.charAt(i);
            if (c == '\\' || c == '(' || c == ')') out.append('\\');
            out.append(c > 255 ? '?' : c);
        }
        return out.toString();
    }

    private void objeto(ByteArrayOutputStream out, int[] xref, int id, String cuerpo) {
        xref[id] = out.size();
        out.writeBytes((id + " 0 obj\n" + cuerpo + "\nendobj\n").getBytes(LATIN));
    }
}
