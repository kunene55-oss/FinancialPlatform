{{- define "common.deployment" -}}
apiVersion: apps/v1
kind: Deployment
metadata:
  name: {{ include "common.fullname" . }}
  labels:
    {{- include "common.labels" . | nindent 4 }}
spec:
  {{- if not .Values.autoscaling.enabled }}
  replicas: {{ .Values.replicaCount }}
  {{- end }}
  selector:
    matchLabels:
      {{- include "common.selectorLabels" . | nindent 6 }}
  template:
    metadata:
      labels:
        {{- include "common.selectorLabels" . | nindent 8 }}
      {{- with .Values.podAnnotations }}
      annotations:
        {{- toYaml . | nindent 8 }}
      {{- end }}
    spec:
      serviceAccountName: {{ include "common.serviceAccountName" . }}
      containers:
        - name: {{ include "common.name" . }}
          image: "{{ .Values.image.repository }}:{{ .Values.image.tag }}"
          imagePullPolicy: {{ .Values.image.pullPolicy }}
          ports:
            - name: http
              containerPort: {{ .Values.containerPort }}
              protocol: TCP
          env:
            {{- if .Values.secrets.configServerPassword.existingSecret }}
            - name: CONFIG_SERVER_USER
              value: {{ .Values.configServerUser | default "configclient" | quote }}
            {{- end }}
            {{- with .Values.env }}
            {{- toYaml . | nindent 12 }}
            {{- end }}
          {{- with .Values.extraEnvFrom }}
          envFrom:
            {{- toYaml . | nindent 12 }}
          {{- end }}
          {{- if .Values.probes.enabled }}
          {{- if eq .Values.probes.type "tcp" }}
          livenessProbe:
            tcpSocket:
              port: http
            initialDelaySeconds: {{ .Values.probes.initialDelaySeconds }}
            periodSeconds: {{ .Values.probes.periodSeconds }}
          readinessProbe:
            tcpSocket:
              port: http
            initialDelaySeconds: {{ .Values.probes.readinessInitialDelaySeconds | default 10 }}
            periodSeconds: {{ .Values.probes.periodSeconds }}
          {{- else }}
          livenessProbe:
            httpGet:
              path: {{ .Values.probes.path }}
              port: http
            initialDelaySeconds: {{ .Values.probes.initialDelaySeconds }}
            periodSeconds: {{ .Values.probes.periodSeconds }}
          readinessProbe:
            httpGet:
              path: {{ .Values.probes.path }}
              port: http
            initialDelaySeconds: {{ .Values.probes.readinessInitialDelaySeconds | default 10 }}
            periodSeconds: {{ .Values.probes.periodSeconds }}
          {{- end }}
          {{- end }}
          resources:
            {{- toYaml .Values.resources | nindent 12 }}
          {{- if or .Values.secrets.configServerPassword.existingSecret .Values.secrets.vaultApprole.enabled }}
          volumeMounts:
            {{- if .Values.secrets.configServerPassword.existingSecret }}
            - name: configserver-password
              mountPath: /run/secrets
              readOnly: true
            {{- end }}
            {{- if .Values.secrets.vaultApprole.enabled }}
            - name: vault-approle
              mountPath: /vault/approle
              readOnly: true
            {{- end }}
          {{- end }}
      {{- if or .Values.secrets.configServerPassword.existingSecret .Values.secrets.vaultApprole.enabled }}
      volumes:
        {{- if .Values.secrets.configServerPassword.existingSecret }}
        - name: configserver-password
          secret:
            secretName: {{ .Values.secrets.configServerPassword.existingSecret }}
            items:
              - key: {{ .Values.secrets.configServerPassword.secretKey | default "password" }}
                path: configserver_password
        {{- end }}
        {{- if .Values.secrets.vaultApprole.enabled }}
        - name: vault-approle
          secret:
            secretName: {{ .Values.secrets.vaultApprole.existingSecret }}
            items:
              - key: {{ .Values.secrets.vaultApprole.roleIdKey | default "role-id" }}
                path: {{ include "common.name" . }}-role-id
              - key: {{ .Values.secrets.vaultApprole.secretIdKey | default "secret-id" }}
                path: {{ include "common.name" . }}-secret-id
        {{- end }}
      {{- end }}
      {{- with .Values.nodeSelector }}
      nodeSelector:
        {{- toYaml . | nindent 8 }}
      {{- end }}
      {{- with .Values.affinity }}
      affinity:
        {{- toYaml . | nindent 8 }}
      {{- end }}
      {{- with .Values.tolerations }}
      tolerations:
        {{- toYaml . | nindent 8 }}
      {{- end }}
{{- end -}}
